package cn.cordys.crm.order;

import cn.cordys.crm.order.controller.OrderController;
import cn.cordys.crm.order.dto.request.OrderStatisticRequest;
import cn.cordys.crm.order.service.OrderCustomerAccessService;
import cn.cordys.crm.order.service.OrderService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.context.OrganizationContext;
import cn.cordys.security.SessionUtils;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.crm.order.dto.response.OrderStatisticResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderStatisticRequestTest {
    @Test void statisticsEndpointAcceptsExistingRequestsWithoutPagination() throws Exception {
        var controller = new OrderController();
        var orders = mock(OrderService.class);
        var scopes = mock(DataScopeService.class);
        ReflectionTestUtils.setField(controller,"orderService",orders);
        ReflectionTestUtils.setField(controller,"dataScopeService",scopes);
        ReflectionTestUtils.setField(controller,"customerAccessService",mock(OrderCustomerAccessService.class));
        when(scopes.getDeptDataPermission(any(),any(),any(),any())).thenReturn(new DeptDataPermissionDTO());
        when(orders.searchStatistic(any(),any(),any(),any())).thenReturn(new OrderStatisticResponse());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        try (var user = mockStatic(SessionUtils.class); var org = mockStatic(OrganizationContext.class);
             var filters = mockStatic(ConditionFilterUtils.class)) {
            user.when(SessionUtils::getUserId).thenReturn("viewer");
            org.when(OrganizationContext::getOrganizationId).thenReturn("org");
            mvc.perform(post("/order/statistic").contentType("application/json")
                    .content("{\"viewId\":\"SELF\",\"customerId\":\"customer\"}"))
                    .andExpect(status().isOk());
        }
        verify(orders).searchStatistic(argThat(request -> request instanceof OrderStatisticRequest statistic
                && "customer".equals(statistic.getCustomerId())),any(),any(),any());
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            assertTrue(validator.getValidator().validate(new OrderStatisticRequest()).isEmpty());
        }
    }
}
