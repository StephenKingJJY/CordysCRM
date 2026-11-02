package cn.cordys.crm.order;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.permission.*;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.crm.approval.service.ApprovalFlowService;
import cn.cordys.crm.approval.dto.StatusPermissionDTO;
import cn.cordys.crm.approval.dto.response.StatusPermissionSettingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderSharedPermissionTest {
    @Test void sharedReadAndExplicitOrderEditStillRequireRoleAndStatusWhileBatchFinancialWritesStayDenied() {
        var service = new ResourcePermissionService();
        var scopes = mock(DataScopeService.class);
        var flow = mock(ApprovalFlowService.class);
        var provider = mock(ResourceAccessContextProvider.class);
        ReflectionTestUtils.setField(service, "dataScopeService", scopes);
        ReflectionTestUtils.setField(service, "approvalFlowService", flow);
        var context = new ResourceAccessContext(); context.setOwnerId("seller"); context.setApprovalStatus("NONE");
        when(provider.getFormType()).thenReturn("order");
        when(provider.getAccessContext("order", "org")).thenReturn(context);
        when(provider.hasSharedDataPermission(anyString(), eq("order"), eq("helper"), eq("org"))).thenReturn(true);
        when(provider.batchGetOwnerIds(List.of("order"), "org")).thenReturn(Map.of("order", "seller"));
        doThrow(new IllegalStateException("ordinary scope denied")).when(scopes)
                .checkDataPermission(eq("helper"), eq("org"), anyList(), anyString());
        try (var beans = mockStatic(CommonBeanFactory.class); var permissions = mockStatic(PermissionUtils.class)) {
            beans.when(() -> CommonBeanFactory.getBeansOfType(ResourceAccessContextProvider.class)).thenReturn(Map.of("order", provider));
            permissions.when(() -> PermissionUtils.hasPermission(anyString())).thenReturn(true);
            assertDoesNotThrow(() -> service.checkResourcePermission(PermissionConstants.ORDER_READ, "order", "order", "helper", "org"));
            assertThrows(RuntimeException.class, () -> service.checkResourcePermission(PermissionConstants.ORDER_UPDATE, "order", "order", "helper", "org"));
            assertDoesNotThrow(() -> service.checkSharedUpdatePermission(PermissionConstants.ORDER_UPDATE, "order", "order", "helper", "org"));
            assertThrows(RuntimeException.class, () -> service.checkBatchResourcePermission(PermissionConstants.ORDER_UPDATE, List.of("order"), "order", "helper", "org"));
            assertThrows(RuntimeException.class, () -> service.checkResourcePermission(PermissionConstants.ORDER_DELETE, "order", "order", "helper", "org"));
            assertThrows(RuntimeException.class, () -> service.checkResourcePermission(PermissionConstants.ORDER_EXPORT, "order", "order", "helper", "org"));
            permissions.when(() -> PermissionUtils.hasPermission(PermissionConstants.ORDER_UPDATE)).thenReturn(false);
            assertThrows(RuntimeException.class, () -> service.checkSharedUpdatePermission(PermissionConstants.ORDER_UPDATE, "order", "order", "helper", "org"));
            permissions.when(() -> PermissionUtils.hasPermission(PermissionConstants.ORDER_UPDATE)).thenReturn(true);
            context.setApprovalStatus("APPROVING");
            var status = new StatusPermissionDTO(); status.setPermission(PermissionConstants.ORDER_UPDATE);
            status.setApprovalStatus("APPROVING"); status.setEnabled(false);
            var setting = new StatusPermissionSettingResponse(); setting.setStatusPermissions(List.of(status));
            when(flow.getStatusPermissionsByFormType("order", "org")).thenReturn(setting);
            assertThrows(RuntimeException.class, () -> service.checkSharedUpdatePermission(PermissionConstants.ORDER_UPDATE, "order", "order", "helper", "org"));
            assertThrows(RuntimeException.class, () -> service.checkResourcePermission(PermissionConstants.ORDER_READ, "missing", "order", "helper", "org"));
        }
    }
}
