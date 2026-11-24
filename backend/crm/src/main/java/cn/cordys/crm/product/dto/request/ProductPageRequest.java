package cn.cordys.crm.product.dto.request;

import cn.cordys.common.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;


/**
 * @author guoyuqi
 */
@Data
public class ProductPageRequest extends BasePageRequest {

    @Schema(description = "状态：1-上架，2-下架")
    private String status;

    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    @Schema(description = "领星店铺；仅返回该店铺产品及参考库存")
    private String lingxingStoreId;

    @JsonIgnore
    private String lingxingStockFieldId;

}
