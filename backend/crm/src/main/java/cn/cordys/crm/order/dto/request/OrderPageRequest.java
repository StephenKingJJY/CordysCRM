package cn.cordys.crm.order.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
public class OrderPageRequest extends BasePageRequest {

    @Schema(description = "看板模式")
    private Boolean board = false;

    @Schema(description = "关联客户id")
    private String customerId;

    @Schema(description = "关联合同id")
    private String contractId;
}
