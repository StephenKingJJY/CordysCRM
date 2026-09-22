package cn.cordys.crm.order.service;

import cn.cordys.aspectj.annotation.OperationLog;
import cn.cordys.aspectj.constants.LogModule;
import cn.cordys.aspectj.constants.LogType;
import cn.cordys.aspectj.context.OperationLogContext;
import cn.cordys.common.constants.FormKey;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.order.domain.OrderSnapshot;
import cn.cordys.crm.order.dto.request.OrderSyncMetadataRequest;
import cn.cordys.crm.order.dto.response.OrderGetResponse;
import cn.cordys.crm.order.mapper.ExtOrderSyncMapper;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.crm.system.domain.ModuleField;
import cn.cordys.crm.system.dto.request.ResourceBatchEditRequest;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderSyncMetadataService {
    private static final Set<String> ALLOWED = Set.of("lingxingOrderSyncStatus", "lingxingOrderNumber",
            "lingxingOrderSyncTime", "lingxingOrderSyncError", "lingxingOrderSyncFingerprint",
            "lingxingTrackingNumber", "lingxingLogisticsCompany");
    private final ExtOrderSyncMapper mapper;
    private final OrderFieldService fields;
    private final ModuleFormService forms;
    @Resource
    private BaseMapper<OrderSnapshot> snapshots;

    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = LogModule.ORDER_INDEX, type = LogType.UPDATE, resourceId = "{#orderId}")
    public void update(String orderId, String orgId, String userId, OrderSyncMetadataRequest request) {
        var values = request.getValues();
        if (values == null || values.isEmpty() || !ALLOWED.containsAll(values.keySet())
                || values.values().stream().anyMatch(v -> v == null || v.length() > 5000)) {
            throw error("order.sync.fields.invalid");
        }
        var order = mapper.lockOrder(orderId, orgId);
        if (order == null) throw error("order_not_exist");
        if (!Objects.equals(order.getUpdateTime(), request.getExpectedUpdateTime())) throw error("order.sync.changed");
        if ("同步中".equals(values.get("lingxingOrderSyncStatus"))
                && (!"APPROVED".equals(order.getApprovalStatus()) || !"CREATE".equals(order.getStage()))) {
            throw error("order.sync.approval.required");
        }
        var allFields = forms.getAllFields(FormKey.ORDER.getKey(), orgId);
        List<BaseModuleFieldValue> updates = new ArrayList<>();
        for (var entry : values.entrySet()) {
            var matches = allFields.stream().filter(f -> entry.getKey().equals(f.getInternalKey())).toList();
            if (matches.size() != 1 || !Set.of("INPUT", "TEXTAREA").contains(matches.getFirst().getType())) {
                throw error("order.sync.fields.invalid");
            }
            var field = matches.getFirst();
            var moduleField = new ModuleField();
            moduleField.setId(field.getId());
            moduleField.setType(field.getType());
            var patch = new ResourceBatchEditRequest();
            patch.setIds(List.of(orderId));
            patch.setFieldId(field.getId());
            patch.setFieldValue(entry.getValue());
            fields.batchDeleteFieldValues(patch, moduleField);
            fields.batchUpdateFieldValues(patch, field, moduleField);
            updates.add(new BaseModuleFieldValue(field.getId(), entry.getValue()));
        }
        // Patch only the allowlisted fields; never rewrite amount, products, approval state or business timestamps.
        var existing = snapshots.selectListByLambda(new LambdaQueryWrapper<OrderSnapshot>().eq(OrderSnapshot::getOrderId, orderId));
        for (var snapshot : existing) {
            var detail = JSON.parseObject(snapshot.getOrderValue(), OrderGetResponse.class);
            Map<String, BaseModuleFieldValue> merged = new LinkedHashMap<>();
            if (detail.getModuleFields() != null) detail.getModuleFields().forEach(f -> merged.put(f.getFieldId(), f));
            updates.forEach(f -> merged.put(f.getFieldId(), f));
            detail.setModuleFields(new ArrayList<>(merged.values()));
            snapshot.setOrderValue(JSON.toJSONString(detail));
            snapshots.update(snapshot);
        }
        OperationLogContext.setResourceName(order.getName());
    }

    private GenericException error(String key) { return new GenericException(Translator.get(key)); }
}
