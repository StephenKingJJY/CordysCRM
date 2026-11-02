package cn.cordys.crm.order.dto.request;

import cn.cordys.common.dto.condition.BaseCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Statistics accept the same filters without requiring pagination parameters. */
@Data
public class OrderStatisticRequest extends BaseCondition {
    @Schema(description = "关联客户id")
    private String customerId;

    @Schema(description = "关联合同id")
    private String contractId;
}
