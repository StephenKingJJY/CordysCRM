package cn.cordys.crm.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OrderPaymentResponse {
    private String id;
    private String orderId;
    private String organizationId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;
    private LocalDate receivedDate;
    private String remark;
    private String createUser;
    private String createUserName;
    private Long createTime;
    private boolean voided;
    private String voidUser;
    private String voidUserName;
    private Long voidTime;
    private String voidReason;
    private List<Receipt> receipts = List.of();

    public record Receipt(String id, String name) {}
}
