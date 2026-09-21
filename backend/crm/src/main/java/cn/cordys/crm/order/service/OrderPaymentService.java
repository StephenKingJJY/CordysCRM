package cn.cordys.crm.order.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.dto.request.OrderPaymentAddRequest;
import cn.cordys.crm.order.dto.response.OrderPaymentResponse;
import cn.cordys.crm.order.dto.response.OrderPaymentSummary;
import cn.cordys.crm.order.mapper.ExtOrderPaymentMapper;
import cn.cordys.crm.system.dto.request.UploadTransferRequest;
import cn.cordys.crm.system.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {
    private final ExtOrderPaymentMapper mapper;
    private final AttachmentService attachmentService;

    public OrderPaymentSummary list(String orderId, String orgId) {
        Order order = requireOrder(mapper.getOrder(orderId, orgId));
        List<OrderPaymentResponse> records = mapper.list(orderId, orgId);
        records.forEach(p -> p.setReceipts(mapper.receipts(p.getId(), orderId, orgId)));
        return summarize(order.getAmount(), records);
    }

    public static OrderPaymentSummary summarize(BigDecimal amount, List<OrderPaymentResponse> records) {
        BigDecimal total = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal received = records.stream().filter(p -> !p.isVoided()).map(OrderPaymentResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = total.subtract(received);
        String status = received.signum() == 0 ? "UNPAID" : balance.signum() > 0 ? "PARTIAL"
                : balance.signum() == 0 ? "PAID" : "OVERPAID";
        return new OrderPaymentSummary(total, received, balance.max(BigDecimal.ZERO),
                balance.negate().max(BigDecimal.ZERO), status, records);
    }

    @Transactional(rollbackFor = Exception.class)
    public String add(String orderId, String orgId, String userId, OrderPaymentAddRequest request,
                      List<MultipartFile> files) {
        requireOrder(mapper.lockOrder(orderId, orgId));
        OrderPaymentResponse previous = mapper.get(request.getId());
        if (previous != null) {
            if (!Objects.equals(previous.getOrderId(), orderId) || !Objects.equals(previous.getOrganizationId(), orgId)
                    || !Objects.equals(previous.getCreateUser(), userId)
                    || previous.getAmount().compareTo(request.getAmount()) != 0
                    || !Objects.equals(previous.getReceivedDate(), request.getReceivedDate())
                    || !Objects.equals(previous.getRemark(), Objects.toString(request.getRemark(), ""))) {
                throw new GenericException(Translator.get("order.payment.retry.conflict"));
            }
            return previous.getId();
        }
        List<MultipartFile> receipts = files == null ? List.of() : files;
        if (receipts.size() > 5 || receipts.stream().anyMatch(file -> file.isEmpty() || file.getSize() > 10 * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().matches("(?i)[^/\\\\]+\\.(pdf|png|jpg|jpeg|webp)"))) {
            throw new GenericException(Translator.get("order.payment.receipt.invalid"));
        }
        OrderPaymentResponse payment = new OrderPaymentResponse();
        payment.setId(request.getId());
        payment.setOrderId(orderId);
        payment.setOrganizationId(orgId);
        payment.setAmount(request.getAmount());
        payment.setReceivedDate(request.getReceivedDate());
        payment.setRemark(Objects.toString(request.getRemark(), ""));
        payment.setCreateUser(userId);
        payment.setCreateTime(System.currentTimeMillis());
        mapper.insert(payment);
        if (!receipts.isEmpty()) {
            List<String> ids = attachmentService.uploadTemp(receipts);
            attachmentService.processTemp(new UploadTransferRequest(orgId, payment.getId(), userId, ids));
            if (mapper.receipts(payment.getId(), orderId, orgId).size() != receipts.size()) {
                throw new GenericException(Translator.get("order.payment.receipt.failed"));
            }
        }
        return payment.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidPayment(String orderId, String id, String orgId, String userId, String reason) {
        requireOrder(mapper.lockOrder(orderId, orgId));
        OrderPaymentResponse payment = mapper.get(id);
        if (payment == null || !Objects.equals(orderId, payment.getOrderId())
                || !Objects.equals(orgId, payment.getOrganizationId())) {
            throw new GenericException(Translator.get("order.payment.not.found"));
        }
        mapper.voidPayment(id, orderId, orgId, userId, System.currentTimeMillis(), reason.trim());
    }

    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> receipt(
            String orderId, String id, String fileId, String orgId) {
        requireOrder(mapper.getOrder(orderId, orgId));
        if (mapper.receipts(id, orderId, orgId).stream().noneMatch(f -> f.id().equals(fileId))) {
            throw new GenericException(Translator.get("order.payment.not.found"));
        }
        var response = attachmentService.getResource(fileId);
        if (response == null) throw new GenericException(Translator.get("order.payment.not.found"));
        return response;
    }

    private Order requireOrder(Order order) {
        if (order == null) throw new GenericException(Translator.get("order_not_exist"));
        return order;
    }
}
