package cn.cordys.crm.order.controller;

import cn.cordys.common.constants.FormKeyConstants;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.permission.CsPermission;
import cn.cordys.common.permission.CsBatchPermission;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.order.dto.request.OrderPaymentAddRequest;
import cn.cordys.crm.order.dto.request.OrderPaymentVoidRequest;
import cn.cordys.crm.order.dto.response.OrderPaymentSummary;
import cn.cordys.crm.order.service.OrderPaymentService;
import cn.cordys.security.SessionUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/order/{orderId}/payments")
@RequiredArgsConstructor
public class OrderPaymentController {
    private final OrderPaymentService service;

    @GetMapping
    @CsPermission(value = PermissionConstants.ORDER_READ, resourceId = "{#orderId}", formType = FormKeyConstants.ORDER)
    public OrderPaymentSummary list(@PathVariable String orderId) {
        return service.list(orderId, OrganizationContext.getOrganizationId());
    }

    // Receipt bookkeeping changes neither the order nor its approval state.
    // Check role + order data scope without requiring an editable approval status.
    @PostMapping(consumes = "multipart/form-data")
    @CsBatchPermission(value = PermissionConstants.ORDER_UPDATE, resourceId = "{#orderId}", formType = FormKeyConstants.ORDER)
    public String add(@PathVariable String orderId, @Valid @RequestPart("request") OrderPaymentAddRequest request,
                      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.add(orderId, OrganizationContext.getOrganizationId(), SessionUtils.getUserId(), request, files);
    }

    @PostMapping("/{id}/void")
    @CsBatchPermission(value = PermissionConstants.ORDER_UPDATE, resourceId = "{#orderId}", formType = FormKeyConstants.ORDER)
    public void voidPayment(@PathVariable String orderId, @PathVariable String id,
                            @Valid @RequestBody OrderPaymentVoidRequest request) {
        service.voidPayment(orderId, id, OrganizationContext.getOrganizationId(), SessionUtils.getUserId(), request.getReason());
    }

    @GetMapping("/{id}/receipts/{fileId}")
    @CsPermission(value = PermissionConstants.ORDER_READ, resourceId = "{#orderId}", formType = FormKeyConstants.ORDER)
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> receipt(
            @PathVariable String orderId, @PathVariable String id, @PathVariable String fileId) {
        return service.receipt(orderId, id, fileId, OrganizationContext.getOrganizationId());
    }
}
