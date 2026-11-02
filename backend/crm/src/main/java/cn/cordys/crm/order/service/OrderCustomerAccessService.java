package cn.cordys.crm.order.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.constants.InternalUserView;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.customer.constants.CustomerCollaborationType;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.crm.customer.domain.CustomerCollaboration;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.dto.request.OrderUpdateRequest;
import cn.cordys.crm.order.dto.response.OrderListResponse;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Live customer relationships extend order read/edit scope, never ownership or financial authority. */
@Service
public class OrderCustomerAccessService {
    @Resource
    private BaseMapper<Customer> customerMapper;
    @Resource
    private BaseMapper<CustomerCollaboration> collaborationMapper;
    @Resource
    private DataScopeService dataScopeService;

    public void includeCustomerOrders(DeptDataPermissionDTO permission) {
        if (permission == null) {
            return;
        }
        permission.setIncludeCustomerOrders(!InternalUserView.isDepartment(permission.getViewId())
                && !Boolean.TRUE.equals(permission.getInvisible()));
        if (Boolean.TRUE.equals(permission.getVisible())) {
            permission.setSelf(true);
        }
    }

    public boolean hasAccess(Order order, String permission, String userId, String orgId) {
        if (order == null || !Objects.equals(orgId, order.getOrganizationId()) || StringUtils.isBlank(order.getCustomerId())
                || StringUtils.isBlank(userId)) {
            return false;
        }
        Customer customer = customerMapper.selectByPrimaryKey(order.getCustomerId());
        if (customer == null || !Objects.equals(orgId, customer.getOrganizationId())) {
            return false;
        }
        CustomerCollaboration example = new CustomerCollaboration();
        example.setCustomerId(customer.getId());
        example.setUserId(userId);
        return permits(customer, collaborationMapper.select(example), permission, userId);
    }

    private boolean permits(Customer customer, List<CustomerCollaboration> links, String permission, String userId) {
        if (!PermissionConstants.ORDER_READ.equals(permission) && !PermissionConstants.ORDER_UPDATE.equals(permission)) {
            return false;
        }
        if (Objects.equals(customer.getOwner(), userId)) {
            return true;
        }
        return links.stream().anyMatch(link ->
                CustomerCollaborationType.COLLABORATION.name().equals(link.getCollaborationType())
                        || (PermissionConstants.ORDER_READ.equals(permission)
                        && CustomerCollaborationType.READ_ONLY.name().equals(link.getCollaborationType())));
    }

    public void populateAccess(List<? extends OrderListResponse> orders, String userId, String orgId) {
        if (orders.isEmpty() || StringUtils.isBlank(userId)) {
            return;
        }
        List<String> customerIds = orders.stream().map(Order::getCustomerId).filter(StringUtils::isNotBlank).distinct().toList();
        if (customerIds.isEmpty()) {
            return;
        }
        Map<String, Customer> customers = customerMapper.selectByIds(customerIds).stream()
                .filter(c -> Objects.equals(orgId, c.getOrganizationId()))
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        LambdaQueryWrapper<CustomerCollaboration> query = new LambdaQueryWrapper<>();
        query.in(CustomerCollaboration::getCustomerId, customerIds).eq(CustomerCollaboration::getUserId, userId);
        Map<String, List<CustomerCollaboration>> links = collaborationMapper.selectListByLambda(query).stream()
                .collect(Collectors.groupingBy(CustomerCollaboration::getCustomerId));
        for (OrderListResponse order : orders) {
            order.setSharedAccessOnly(false);
            order.setSharedEditable(false);
            Customer customer = customers.get(order.getCustomerId());
            if (customer == null || !Objects.equals(orgId, order.getOrganizationId())) {
                continue;
            }
            List<CustomerCollaboration> customerLinks = links.getOrDefault(customer.getId(), List.of());
            boolean sharedRead = permits(customer, customerLinks, PermissionConstants.ORDER_READ, userId);
            boolean normalRead = Objects.equals(order.getOwner(), userId)
                    || (StringUtils.isNotBlank(order.getOwner())
                    && dataScopeService.hasDataPermission(userId, orgId, order.getOwner(), PermissionConstants.ORDER_READ));
            order.setSharedAccessOnly(sharedRead && !normalRead);
            order.setSharedEditable(permits(customer, customerLinks, PermissionConstants.ORDER_UPDATE, userId));
        }
    }

    public void checkSharedUpdateIdentity(Order oldOrder, OrderUpdateRequest request, String userId, String orgId) {
        if (oldOrder != null && hasAccess(oldOrder, PermissionConstants.ORDER_UPDATE, userId, orgId)
                && (StringUtils.isBlank(oldOrder.getOwner())
                || !dataScopeService.hasDataPermission(userId, orgId, oldOrder.getOwner(), PermissionConstants.ORDER_UPDATE))
                && (!Objects.equals(oldOrder.getOwner(), request.getOwner())
                || !Objects.equals(oldOrder.getCustomerId(), request.getCustomerId()))) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
    }
}
