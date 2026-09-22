package cn.cordys.crm.order;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.base.BaseTest;
import cn.cordys.crm.order.dto.request.OrderSyncMetadataRequest;
import cn.cordys.crm.order.mapper.ExtOrderSyncMapper;
import cn.cordys.crm.order.service.OrderSyncMetadataService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrderSyncPersistenceTest extends BaseTest {
    @Resource JdbcTemplate jdbc;
    @Resource OrderSyncMetadataService service;
    @Resource ExtOrderSyncMapper mapper;

    @Test @Transactional
    void writesOnlyMetadataAndPreservesBusinessAndApprovalInDatabase() {
        jdbc.update("INSERT INTO sys_module_form(id,form_key,organization_id,create_time,update_time,create_user,update_user) VALUES ('sync-test-form','order','sync-test-org',1,1,'admin','admin')");
        jdbc.update("INSERT INTO sys_module_field(id,form_id,internal_key,name,type,pos,create_user,update_user,create_time,update_time) VALUES ('sync-test-field','sync-test-form','lingxingOrderSyncStatus','Sync','INPUT',1,'admin','admin',1,1)");
        jdbc.update("INSERT INTO sys_module_field_blob(id,prop) VALUES ('sync-test-field',?)", "{\"id\":\"sync-test-field\",\"type\":\"INPUT\",\"name\":\"Sync\",\"required\":false,\"repeat\":true}");
        jdbc.update("INSERT INTO sales_order(id,number,name,amount,stage,organization_id,create_time,update_time,create_user,update_user,approval_status,approved) VALUES ('sync-test-order','SYNC-TEST','Original',100,'CREATE','sync-test-org',1,10,'admin','admin','APPROVED',1)");
        jdbc.update("INSERT INTO sales_order_field(id,resource_id,field_id,field_value) VALUES ('sync-test-business','sync-test-order','business','untouched')");
        assertNull(mapper.lockOrder("sync-test-order","another-org"));
        var request=new OrderSyncMetadataRequest(); request.setExpectedUpdateTime(10L);
        request.setValues(Map.of("lingxingOrderSyncStatus","同步中"));
        OrganizationContext.setOrganizationId("sync-test-org");
        try {
            OrderSyncMetadataService target=AopTestUtils.getTargetObject(service);
            target.update("sync-test-order","sync-test-org","admin",request);
            assertEquals("同步中",jdbc.queryForObject("SELECT field_value FROM sales_order_field WHERE resource_id='sync-test-order' AND field_id='sync-test-field'",String.class));
            request.setValues(Map.of("lingxingOrderSyncStatus","成功"));
            target.update("sync-test-order","sync-test-org","admin",request);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sales_order_field WHERE resource_id='sync-test-order' AND field_id='sync-test-field'",Integer.class));
            assertEquals("成功",jdbc.queryForObject("SELECT field_value FROM sales_order_field WHERE resource_id='sync-test-order' AND field_id='sync-test-field'",String.class));
            assertEquals("untouched",jdbc.queryForObject("SELECT field_value FROM sales_order_field WHERE id='sync-test-business'",String.class));
            var order=mapper.lockOrder("sync-test-order","sync-test-org");
            assertEquals("APPROVED",order.getApprovalStatus()); assertEquals("Original",order.getName());
            assertEquals(100,order.getAmount().intValueExact()); assertEquals(10L,order.getUpdateTime());
        } finally { OrganizationContext.clear(); }
    }
}
