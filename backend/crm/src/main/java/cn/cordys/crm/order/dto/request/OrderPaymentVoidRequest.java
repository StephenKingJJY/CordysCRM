package cn.cordys.crm.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderPaymentVoidRequest {
    @NotBlank @Size(max = 500)
    private String reason;
}
