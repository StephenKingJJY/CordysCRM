package cn.cordys.crm.order.controller;

import cn.cordys.common.constants.FormKeyConstants;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.permission.CsBatchPermission;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.order.dto.request.OrderSyncMetadataRequest;
import cn.cordys.crm.order.service.OrderSyncMetadataService;
import cn.cordys.security.SessionUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order/{orderId}/lingxing-sync")
@RequiredArgsConstructor
public class OrderSyncController {
    private final OrderSyncMetadataService service;

    // Only integration metadata is writable; pending business content remains locked by its approval.
    @PostMapping
    @CsBatchPermission(value = PermissionConstants.ORDER_UPDATE, resourceId = "{#orderId}", formType = FormKeyConstants.ORDER)
    public void update(@PathVariable String orderId, @Valid @RequestBody OrderSyncMetadataRequest request) {
        service.update(orderId, OrganizationContext.getOrganizationId(), SessionUtils.getUserId(), request);
    }
}
