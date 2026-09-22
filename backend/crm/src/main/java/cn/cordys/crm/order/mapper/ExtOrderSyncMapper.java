package cn.cordys.crm.order.mapper;

import cn.cordys.crm.order.domain.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ExtOrderSyncMapper {
    @Select("SELECT * FROM sales_order WHERE id=#{id} AND organization_id=#{orgId} FOR UPDATE")
    Order lockOrder(@Param("id") String id, @Param("orgId") String orgId);
}
