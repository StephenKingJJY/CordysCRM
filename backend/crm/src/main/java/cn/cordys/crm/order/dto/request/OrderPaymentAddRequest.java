package cn.cordys.crm.order.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OrderPaymentAddRequest {
    // Client-generated idempotency key, retained when retrying the same submission.
    @NotBlank @Pattern(regexp = "[a-f0-9]{32}")
    private String id;
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;
    @NotNull @PastOrPresent
    private LocalDate receivedDate;
    @Size(max = 2000)
    private String remark;
}
