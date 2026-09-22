package cn.cordys.crm.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

@Data
public class OrderSyncMetadataRequest {
    @NotNull
    private Long expectedUpdateTime;
    @NotEmpty
    @Size(max = 7)
    private Map<String, String> values;
}
