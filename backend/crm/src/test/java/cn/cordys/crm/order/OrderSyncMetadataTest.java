package cn.cordys.crm.order;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.domain.OrderSnapshot;
import cn.cordys.crm.order.dto.request.OrderSyncMetadataRequest;
import cn.cordys.crm.order.dto.response.OrderGetResponse;
import cn.cordys.crm.order.mapper.ExtOrderSyncMapper;
import cn.cordys.crm.order.service.OrderFieldService;
import cn.cordys.crm.order.service.OrderSyncMetadataService;
import cn.cordys.crm.system.dto.field.InputField;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderSyncMetadataTest {
    ExtOrderSyncMapper mapper = mock(ExtOrderSyncMapper.class);
    OrderFieldService fields = mock(OrderFieldService.class);
    ModuleFormService forms = mock(ModuleFormService.class);
    BaseMapper<OrderSnapshot> snapshots = mock(BaseMapper.class);
    OrderSyncMetadataService service = new OrderSyncMetadataService(mapper, fields, forms);
    Order order;

    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"snapshots",snapshots);
        order = new Order(); order.setId("o"); order.setOrganizationId("org"); order.setName("Original");
        order.setStage("CREATE"); order.setApprovalStatus("APPROVED"); order.setUpdateTime(10L);
        order.setAmount(new BigDecimal("100.00"));
        when(mapper.lockOrder("o","org")).thenReturn(order);
        var field = new InputField(); field.setId("sync"); field.setType("INPUT"); field.setInternalKey("lingxingOrderSyncStatus");
        when(forms.getAllFields("order","org")).thenReturn(List.of(field));
        when(snapshots.selectListByLambda(any())).thenReturn(List.of());
    }
    OrderSyncMetadataRequest request(Map<String,String> values) {
        var r=new OrderSyncMetadataRequest(); r.setExpectedUpdateTime(10L); r.setValues(values); return r;
    }
    void rejected(OrderSyncMetadataRequest r) {
        try(var translator=mockStatic(Translator.class)) {
            translator.when(()->Translator.get(anyString())).thenAnswer(i->i.getArgument(0));
            assertThrows(GenericException.class,()->service.update("o","org","u",r));
        }
        verifyNoInteractions(fields);
    }
    @Test void blocksBusinessFieldsAndUnknownKeys() {
        rejected(request(Map.of("amount","1")));
        rejected(request(Map.of("lingxingOrderSyncStatus","同步中","orderProducts","[]")));
    }
    @Test void rejectsCrossOrganizationOrMissingOrder() {
        when(mapper.lockOrder("o","org")).thenReturn(null);
        rejected(request(Map.of("lingxingOrderSyncStatus","同步中")));
    }
    @Test void rejectsStaleBusinessVersion() {
        order.setUpdateTime(11L); rejected(request(Map.of("lingxingOrderSyncStatus","同步中")));
    }
    @Test void requiresManualApprovalAndCreateStageBeforeClaim() {
        for (String status:List.of("NONE","PENDING","APPROVING","UNAPPROVED","REVOKED","AUTO_APPROVED")) {
            order.setApprovalStatus(status); rejected(request(Map.of("lingxingOrderSyncStatus","同步中")));
        }
        order.setApprovalStatus("APPROVED"); order.setStage("SHIPPED");
        rejected(request(Map.of("lingxingOrderSyncStatus","同步中")));
    }
    @Test void patchesMetadataWithoutChangingBusinessOrApprovalSnapshot() {
        var value=new OrderGetResponse(); value.setName("Original"); value.setAmount(new BigDecimal("100.00"));
        value.setApprovalStatus("APPROVED"); value.setModuleFields(List.of(new BaseModuleFieldValue("products",List.of("original"))));
        var snapshot=new OrderSnapshot(); snapshot.setId("s"); snapshot.setOrderId("o"); snapshot.setOrderValue(JSON.toJSONString(value));
        when(snapshots.selectListByLambda(any())).thenReturn(List.of(snapshot));
        service.update("o","org","u",request(Map.of("lingxingOrderSyncStatus","同步中")));
        verify(fields).batchUpdateFieldValues(argThat(p -> p.getIds().equals(List.of("o")) && p.getFieldId().equals("sync") && p.getFieldValue().equals("同步中")),any(),any());
        var after=JSON.parseObject(snapshot.getOrderValue(),OrderGetResponse.class);
        assertEquals(value.getName(),after.getName()); assertEquals(value.getAmount(),after.getAmount());
        assertEquals("APPROVED",after.getApprovalStatus()); assertEquals(value.getModuleFields().getFirst(),after.getModuleFields().getFirst());
        assertEquals("APPROVED",order.getApprovalStatus()); assertEquals(10L,order.getUpdateTime());
        verify(snapshots).update(snapshot);
    }
    @Test void legacyOrderMetadataDoesNotRequireNewApproval() {
        order.setApprovalStatus("NONE"); order.setStage("SHIPPED");
        service.update("o","org","u",request(Map.of("lingxingOrderSyncStatus","成功")));
        assertEquals("NONE",order.getApprovalStatus());
        verify(fields).batchUpdateFieldValues(any(),any(),any());
    }
    @Test void readOnlyUserCannotReachSyncMetadataWrites() {
        var permissions=mock(cn.cordys.common.permission.ResourcePermissionService.class);
        var aspect=new cn.cordys.common.permission.CsPermissionAspect();
        org.springframework.test.util.ReflectionTestUtils.setField(aspect,"resourcePermissionService",permissions);
        var delegate=mock(OrderSyncMetadataService.class);
        var factory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                new cn.cordys.crm.order.controller.OrderSyncController(delegate));
        factory.addAspect(aspect);
        cn.cordys.crm.order.controller.OrderSyncController controller=factory.getProxy();
        try(var user=mockStatic(cn.cordys.security.SessionUtils.class);
            var org=mockStatic(cn.cordys.context.OrganizationContext.class)) {
            user.when(cn.cordys.security.SessionUtils::getUserId).thenReturn("reader");
            org.when(cn.cordys.context.OrganizationContext::getOrganizationId).thenReturn("org");
            doThrow(new IllegalStateException("denied")).when(permissions)
                    .checkBatchResourcePermission("ORDER:UPDATE",List.of("o"),"order","reader","org");
            assertThrows(IllegalStateException.class,()->controller.update("o",request(Map.of("lingxingOrderSyncStatus","成功"))));
            verifyNoInteractions(delegate);
        }
    }
}
