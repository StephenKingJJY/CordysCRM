package cn.cordys.crm.order.mapper;

import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.dto.response.OrderPaymentResponse;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ExtOrderPaymentMapper {
    @Select("SELECT * FROM sales_order WHERE id=#{orderId} AND organization_id=#{orgId}")
    Order getOrder(@Param("orderId") String orderId, @Param("orgId") String orgId);

    @Select("SELECT * FROM sales_order WHERE id=#{orderId} AND organization_id=#{orgId} FOR UPDATE")
    Order lockOrder(@Param("orderId") String orderId, @Param("orgId") String orgId);

    @Select("""
        SELECT p.*, COALESCE(u.name, p.create_user) AS create_user_name,
               COALESCE(v.name, p.void_user) AS void_user_name
        FROM sales_order_payment p
        LEFT JOIN sys_user u ON u.id=p.create_user
        LEFT JOIN sys_user v ON v.id=p.void_user
        WHERE p.order_id=#{orderId} AND p.organization_id=#{orgId}
        ORDER BY p.received_date DESC, p.create_time DESC, p.id DESC
        """)
    List<OrderPaymentResponse> list(@Param("orderId") String orderId, @Param("orgId") String orgId);

    @Select("""
        <script>
        SELECT p.*, COALESCE(u.name,p.create_user) AS create_user_name,
               COALESCE(v.name,p.void_user) AS void_user_name
        FROM sales_order_payment p
        LEFT JOIN sys_user u ON u.id=p.create_user
        LEFT JOIN sys_user v ON v.id=p.void_user
        WHERE p.organization_id=#{orgId} AND p.order_id IN
        <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY p.order_id,p.received_date,p.create_time,p.id
        </script>
        """)
    List<OrderPaymentResponse> listForExport(@Param("orderIds") List<String> orderIds, @Param("orgId") String orgId);

    @Select("""
        <script>
        SELECT a.resource_id AS payment_id,a.name
        FROM sys_attachment a
        JOIN sales_order_payment p ON p.id=a.resource_id AND p.organization_id=a.organization_id
        WHERE p.organization_id=#{orgId} AND p.order_id IN
        <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY p.id,a.create_time,a.id
        </script>
        """)
    List<cn.cordys.crm.order.dto.response.OrderPaymentExportReceipt> receiptsForExport(
            @Param("orderIds") List<String> orderIds, @Param("orgId") String orgId);

    @Select("SELECT * FROM sales_order_payment WHERE id=#{id}")
    OrderPaymentResponse get(@Param("id") String id);

    @Insert("""
        INSERT INTO sales_order_payment
        (id,order_id,organization_id,amount,received_date,remark,create_user,create_time,voided)
        VALUES (#{id},#{orderId},#{organizationId},#{amount},#{receivedDate},#{remark},#{createUser},#{createTime},FALSE)
        """)
    int insert(OrderPaymentResponse payment);

    @Update("""
        UPDATE sales_order_payment SET voided=TRUE,void_user=#{userId},void_time=#{time},void_reason=#{reason}
        WHERE id=#{id} AND order_id=#{orderId} AND organization_id=#{orgId} AND voided=FALSE
        """)
    int voidPayment(@Param("id") String id, @Param("orderId") String orderId,
                    @Param("orgId") String orgId, @Param("userId") String userId,
                    @Param("time") long time, @Param("reason") String reason);

    @Select("""
        SELECT a.id,a.name FROM sys_attachment a
        JOIN sales_order_payment p ON p.id=a.resource_id AND p.organization_id=a.organization_id
        WHERE p.id=#{id} AND p.order_id=#{orderId} AND p.organization_id=#{orgId}
        ORDER BY a.create_time,a.id
        """)
    List<OrderPaymentResponse.Receipt> receipts(@Param("id") String id, @Param("orderId") String orderId,
                                              @Param("orgId") String orgId);

    @Select("SELECT COUNT(*) FROM sales_order_payment WHERE order_id=#{orderId}")
    int count(@Param("orderId") String orderId);
}
