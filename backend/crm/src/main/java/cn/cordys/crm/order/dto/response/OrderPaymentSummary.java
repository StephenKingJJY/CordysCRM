package cn.cordys.crm.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;

public record OrderPaymentSummary(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal orderAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal receivedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remainingAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overpaidAmount,
        String status, List<OrderPaymentResponse> records) {
}
