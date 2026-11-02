package cn.cordys.crm.order;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerCollaboration;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.dto.request.OrderUpdateRequest;
import cn.cordys.crm.order.dto.response.OrderListResponse;
import cn.cordys.crm.order.service.OrderCustomerAccessService;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCustomerAccessTest {
    private final OrderCustomerAccessService service = new OrderCustomerAccessService();
    private final BaseMapper<Customer> customers = mock(BaseMapper.class);
    private final BaseMapper<CustomerCollaboration> links = mock(BaseMapper.class);
    private final DataScopeService scopes = mock(DataScopeService.class);
    private final Order order = new Order();
    private final Customer customer = new Customer();

    @BeforeEach void prepare() {
        ReflectionTestUtils.setField(service, "customerMapper", customers);
        ReflectionTestUtils.setField(service, "collaborationMapper", links);
        ReflectionTestUtils.setField(service, "dataScopeService", scopes);
        order.setId("order"); order.setOrganizationId("org"); order.setCustomerId("customer"); order.setOwner("seller");
        customer.setId("customer"); customer.setOrganizationId("org"); customer.setOwner("customer-owner");
        when(customers.selectByPrimaryKey("customer")).thenReturn(customer);
        when(links.select(any(CustomerCollaboration.class))).thenReturn(List.of());
    }

    private void relationship(String type) {
        var link = new CustomerCollaboration(); link.setCustomerId("customer"); link.setUserId("helper"); link.setCollaborationType(type);
        when(links.select(any(CustomerCollaboration.class))).thenReturn(List.of(link));
    }

    @Test void customerOwnerCanReadAndEditButDoesNotGainFinancialOrDestructiveAuthority() {
        assertTrue(service.hasAccess(order, PermissionConstants.ORDER_READ, "customer-owner", "org"));
        assertTrue(service.hasAccess(order, PermissionConstants.ORDER_UPDATE, "customer-owner", "org"));
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_DELETE, "customer-owner", "org"));
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_EXPORT, "customer-owner", "org"));
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_DOWNLOAD, "customer-owner", "org"));
    }

    @Test void readOnlyRelationshipCannotEditAndCooperationCan() {
        relationship("READ_ONLY");
        assertTrue(service.hasAccess(order, PermissionConstants.ORDER_READ, "helper", "org"));
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_UPDATE, "helper", "org"));
        relationship("COLLABORATION");
        assertTrue(service.hasAccess(order, PermissionConstants.ORDER_UPDATE, "helper", "org"));
        relationship("UNKNOWN");
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_READ, "helper", "org"));
    }

    @Test void removalImmediatelyRevokesRelationshipAndOtherOrganizationsAreRejected() {
        relationship("COLLABORATION");
        assertTrue(service.hasAccess(order, PermissionConstants.ORDER_READ, "helper", "org"));
        when(links.select(any(CustomerCollaboration.class))).thenReturn(List.of());
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_READ, "helper", "org"));
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_READ, "customer-owner", "other"));
        customer.setOrganizationId("other");
        assertFalse(service.hasAccess(order, PermissionConstants.ORDER_READ, "customer-owner", "org"));
        assertFalse(service.hasAccess(null, PermissionConstants.ORDER_READ, "customer-owner", "org"));
    }

    @Test void sharedEditorCannotTransferOwnerOrReassignCustomerButOrdinaryEditorKeepsExistingRights() {
        relationship("COLLABORATION");
        var request = new OrderUpdateRequest(); request.setOwner("seller"); request.setCustomerId("customer");
        assertDoesNotThrow(() -> service.checkSharedUpdateIdentity(order, request, "helper", "org"));
        request.setOwner("helper");
        assertThrows(RuntimeException.class, () -> service.checkSharedUpdateIdentity(order, request, "helper", "org"));
        request.setOwner("seller"); request.setCustomerId("another");
        assertThrows(RuntimeException.class, () -> service.checkSharedUpdateIdentity(order, request, "helper", "org"));
        when(scopes.hasDataPermission("helper", "org", "seller", PermissionConstants.ORDER_UPDATE)).thenReturn(true);
        assertDoesNotThrow(() -> service.checkSharedUpdateIdentity(order, request, "helper", "org"));
    }

    @Test void capabilityFlagsDistinguishSharedReadOnlyEditingAndExistingRoleScope() {
        var row = new OrderListResponse(); row.setId("order"); row.setCustomerId("customer");
        row.setOrganizationId("org"); row.setOwner("seller");
        var link = new CustomerCollaboration(); link.setCustomerId("customer"); link.setUserId("helper");
        link.setCollaborationType("READ_ONLY");
        when(customers.selectByIds(anyList())).thenReturn(List.of(customer));
        when(links.selectListByLambda(any())).thenReturn(List.of(link));
        service.populateAccess(List.of(row), "helper", "org");
        assertTrue(row.getSharedAccessOnly()); assertFalse(row.getSharedEditable());
        link.setCollaborationType("COLLABORATION");
        service.populateAccess(List.of(row), "helper", "org");
        assertTrue(row.getSharedAccessOnly()); assertTrue(row.getSharedEditable());
        when(scopes.hasDataPermission("helper", "org", "seller", PermissionConstants.ORDER_READ)).thenReturn(true);
        service.populateAccess(List.of(row), "helper", "org");
        assertFalse(row.getSharedAccessOnly());
    }

    @Test void onlyReadListsOptInWhileDepartmentAndDeniedScopesStayRestricted() {
        var scope = new DeptDataPermissionDTO(); scope.setSelf(true); scope.setViewId("SELF");
        assertFalse(scope.getIncludeCustomerOrders(), "New scopes used for exports must not inherit relationship access");
        service.includeCustomerOrders(scope); assertTrue(scope.getIncludeCustomerOrders());
        scope.setViewId("DEPARTMENT"); service.includeCustomerOrders(scope); assertFalse(scope.getIncludeCustomerOrders());
        scope.setViewId("ALL"); scope.setInvisible(true); service.includeCustomerOrders(scope); assertFalse(scope.getIncludeCustomerOrders());
        scope.setInvisible(false); scope.setVisible(true); scope.setSelf(false);
        service.includeCustomerOrders(scope); assertTrue(scope.getSelf());
    }
}
