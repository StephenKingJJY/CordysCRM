package cn.cordys.crm.order;

import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.crm.base.BaseTest;
import cn.cordys.crm.order.dto.request.OrderPageRequest;
import cn.cordys.crm.order.mapper.ExtOrderMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.mybatis.spring.SqlSessionTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OrderCustomerVisibilityTest extends BaseTest {
    @Resource JdbcTemplate jdbc;
    @Resource ExtOrderMapper orders;
    @Resource SqlSessionTemplate sqlSession;

    private void customer(String id, String owner, String org) {
        jdbc.update("INSERT INTO customer(id,name,owner,organization_id,in_shared_pool,create_time,update_time,create_user,update_user) VALUES (?,?,?,?,0,1,1,'admin','admin')", id,id,owner,org);
    }

    private void order(String id, String customer, String owner, String org) {
        jdbc.update("INSERT INTO sales_order(id,number,name,customer_id,owner,amount,stage,organization_id,create_time,update_time,create_user,update_user,approval_status,approved) VALUES (?,?,?,?,?,10,'CREATE',?,1,1,'admin','admin','NONE',0)",id,id,id,customer,owner,org);
    }

    private Set<String> ids(OrderPageRequest request, DeptDataPermissionDTO scope) {
        return orders.list(request,"shared-test-org","shared-test-user",scope,false).stream()
                .map(o -> o.getId()).collect(Collectors.toSet());
    }

    @Test @Transactional
    void listStatisticsFiltersRemovalAndExportScopesUseTheSameLiveCustomerRelationships() {
        customer("shared-owner-c","shared-test-user","shared-test-org");
        customer("shared-edit-c","other","shared-test-org");
        customer("shared-read-c","other","shared-test-org");
        customer("shared-other-c","other","shared-test-org");
        customer("shared-foreign-c","shared-test-user","shared-other-org");
        order("shared-own","shared-other-c","shared-test-user","shared-test-org");
        order("shared-owner","shared-owner-c","other","shared-test-org");
        order("shared-edit","shared-edit-c","other","shared-test-org");
        order("shared-read","shared-read-c","other","shared-test-org");
        order("shared-unrelated","shared-other-c","other","shared-test-org");
        order("shared-cross-customer","shared-foreign-c","other","shared-test-org");
        order("shared-cross-order","shared-owner-c","other","shared-other-org");
        for (String[] link : List.of(new String[]{"shared-edit-link","shared-edit-c","COLLABORATION"},
                new String[]{"shared-read-link","shared-read-c","READ_ONLY"},
                new String[]{"shared-duplicate-link","shared-read-c","READ_ONLY"})) {
            jdbc.update("INSERT INTO customer_collaboration(id,customer_id,user_id,collaboration_type,create_time,update_time,create_user,update_user) VALUES (?,?,'shared-test-user',?,1,1,'admin','admin')",link[0],link[1],link[2]);
        }
        var request = new OrderPageRequest(); request.setViewId("SELF");
        var scope = new DeptDataPermissionDTO(); scope.setSelf(true); scope.setViewId("SELF"); scope.setIncludeCustomerOrders(true);
        assertEquals(Set.of("shared-own","shared-owner","shared-edit","shared-read"),ids(request,scope));
        assertEquals(4,orders.list(request,"shared-test-org","shared-test-user",scope,false).size(),"Duplicate collaboration records must not duplicate orders");
        assertEquals(40.0,orders.searchStatistic(request,"shared-test-org","shared-test-user",scope).getAmount());
        request.setCustomerId("shared-read-c");
        assertEquals(Set.of("shared-read"),ids(request,scope));
        assertEquals(10.0,orders.searchStatistic(request,"shared-test-org","shared-test-user",scope).getAmount());
        request.setCustomerId(null); request.setKeyword("shared-edit");
        assertEquals(Set.of("shared-edit"),ids(request,scope));
        assertEquals(10.0,orders.searchStatistic(request,"shared-test-org","shared-test-user",scope).getAmount());
        request.setKeyword("shared-owner-c");
        assertEquals(Set.of("shared-owner"),ids(request,scope));
        assertEquals(10.0,orders.searchStatistic(request,"shared-test-org","shared-test-user",scope).getAmount());
        request.setKeyword(null);
        jdbc.update("DELETE FROM customer_collaboration WHERE customer_id='shared-edit-c'");
        // JDBC fixture writes do not notify MyBatis; a subsequent HTTP request has a fresh session.
        sqlSession.clearCache();
        assertFalse(ids(request,scope).contains("shared-edit"),"Removal must take effect without a permission cache refresh");
        scope.setIncludeCustomerOrders(false);
        assertEquals(Set.of("shared-own"),ids(request,scope),"Export queries retain the original ownership scope");
        assertEquals(1,orders.getListByIds(List.of("shared-own","shared-owner"),"shared-test-user","shared-test-org",scope).size());
        scope.setInvisible(true); scope.setIncludeCustomerOrders(true);
        assertTrue(ids(request,scope).isEmpty());
    }
}
