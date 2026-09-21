package cn.cordys.crm.order;

import cn.cordys.common.constants.FormKeyConstants;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.permission.CsPermissionAspect;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.order.controller.OrderPaymentController;
import cn.cordys.crm.order.dto.request.OrderPaymentAddRequest;
import cn.cordys.crm.order.service.OrderPaymentService;
import cn.cordys.security.SessionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderPaymentPermissionTest {
    @Test void deniedOrderUpdateDoesNotReachPaymentMutation() {
        var service = mock(OrderPaymentService.class);
        var permissions = mock(ResourcePermissionService.class);
        var aspect = new CsPermissionAspect(); ReflectionTestUtils.setField(aspect, "resourcePermissionService", permissions);
        var factory = new AspectJProxyFactory(new OrderPaymentController(service)); factory.addAspect(aspect);
        OrderPaymentController controller = factory.getProxy();
        try (var user = mockStatic(SessionUtils.class); var org = mockStatic(OrganizationContext.class)) {
            user.when(SessionUtils::getUserId).thenReturn("reader"); org.when(OrganizationContext::getOrganizationId).thenReturn("org");
            doThrow(new IllegalStateException("denied")).when(permissions).checkBatchResourcePermission(
                    PermissionConstants.ORDER_UPDATE, List.of("order-a"), FormKeyConstants.ORDER, "reader", "org");
            assertThrows(IllegalStateException.class, () -> controller.add("order-a", new OrderPaymentAddRequest(), List.of()));
            verifyNoInteractions(service);
        }
    }
    @Test void readingPaymentAndReceiptBothCheckExactOrderResource() {
        var service = mock(OrderPaymentService.class);
        var permissions = mock(ResourcePermissionService.class);
        var aspect = new CsPermissionAspect(); ReflectionTestUtils.setField(aspect, "resourcePermissionService", permissions);
        var factory = new AspectJProxyFactory(new OrderPaymentController(service)); factory.addAspect(aspect);
        OrderPaymentController controller = factory.getProxy();
        try (var user = mockStatic(SessionUtils.class); var org = mockStatic(OrganizationContext.class)) {
            user.when(SessionUtils::getUserId).thenReturn("reader"); org.when(OrganizationContext::getOrganizationId).thenReturn("org");
            controller.list("order-a"); controller.receipt("order-a", "p", "f");
            verify(permissions, times(2)).checkResourcePermission(PermissionConstants.ORDER_READ, "order-a", FormKeyConstants.ORDER, "reader", "org");
            verify(service).list("order-a", "org"); verify(service).receipt("order-a", "p", "f", "org");
        }
    }
}
