package cn.cordys.crm.product.service;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.product.domain.Product;
import cn.cordys.crm.product.dto.request.ProductPageRequest;
import cn.cordys.crm.product.dto.response.ProductListResponse;
import cn.cordys.crm.system.dto.field.SelectField;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.field.base.SubField;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.mybatis.BaseMapper;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** Store membership and reference quantities come from the bridge's complete inventory snapshot. */
@Service
@RequiredArgsConstructor
public class LingxingStoreService {
    public static final String STORE = "lingxingOrderStore";
    public static final String STOCKS = "lingxingStoreStocks";
    private static final Set<String> SYNC_FIELDS = Set.of("lingxingOrderSyncStatus", "lingxingOrderNumber",
            "lingxingOrderSyncTime", "lingxingOrderSyncError", "lingxingOrderSyncFingerprint",
            "lingxingTrackingNumber", "lingxingLogisticsCompany");
    private final ModuleFormCacheService forms;
    private final ProductFieldService productFields;
    @Resource
    private BaseMapper<Product> products;

    private List<BaseField> fields(String key, String org) {
        var config = forms.getBusinessFormConfig(key, org);
        return config == null || config.getFields() == null ? List.of() : config.getFields();
    }

    private BaseField field(List<BaseField> fields, String key) {
        return fields.stream().filter(f -> key.equals(f.getInternalKey())).findFirst().orElse(null);
    }

    private SelectField storeField(String org) {
        var field = field(fields(FormKey.ORDER.getKey(), org), STORE);
        return field instanceof SelectField select ? select : null;
    }

    private String store(SelectField field, List<BaseModuleFieldValue> values) {
        Object value = value(values, field.getId());
        String selected = value == null || value.toString().isBlank() ? field.getDefaultValue() : value.toString();
        if (selected == null || !selected.matches("[A-Za-z0-9_-]{1,64}") || field.getOptions() == null
                || field.getOptions().stream().noneMatch(o -> !Boolean.TRUE.equals(o.getDisabled()) && selected.equals(String.valueOf(o.getValue())))) {
            throw error("order.store.invalid");
        }
        return selected;
    }

    private static Object value(List<BaseModuleFieldValue> values, String id) {
        return values == null ? null : values.stream().filter(f -> id.equals(f.getFieldId()))
                .map(BaseModuleFieldValue::getFieldValue).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public void prepareProductQuery(ProductPageRequest request, String org) {
        if (request.getLingxingStoreId() == null || request.getLingxingStoreId().isBlank()) return;
        var storeField = storeField(org);
        if (storeField == null) throw error("order.store.invalid");
        store(storeField, List.of(new BaseModuleFieldValue(storeField.getId(), request.getLingxingStoreId())));
        var stockField = field(fields(FormKey.PRODUCT.getKey(), org), STOCKS);
        if (stockField == null) throw error("order.store.stock.unavailable");
        request.setLingxingStockFieldId(stockField.getId());
    }

    public void applyReferenceStock(List<ProductListResponse> list, String storeId, String org) {
        if (storeId == null || storeId.isBlank()) return;
        var config = fields(FormKey.PRODUCT.getKey(), org);
        var stockField = field(config, STOCKS);
        var referenceField = field(config, "lingxingReferenceStock");
        if (stockField == null || referenceField == null) throw error("order.store.stock.unavailable");
        for (var product : list) {
            var stocks = stocks(value(product.getModuleFields(), stockField.getId()));
            var quantity = quantity(stocks.get(storeId));
            var values = new ArrayList<>(product.getModuleFields() == null ? List.<BaseModuleFieldValue>of() : product.getModuleFields());
            values.removeIf(f -> referenceField.getId().equals(f.getFieldId()) || stockField.getId().equals(f.getFieldId()));
            values.add(new BaseModuleFieldValue(referenceField.getId(), quantity));
            product.setModuleFields(values);
        }
    }

    public boolean isStoreField(String fieldId, String org) {
        var field = storeField(org);
        return field != null && field.getId().equals(fieldId);
    }

    /** Validates authoritative configuration rather than the client-supplied form snapshot. */
    public List<BaseModuleFieldValue> validateOrder(List<BaseModuleFieldValue> values,
                                                   List<BaseModuleFieldValue> original, String org) {
        return validateOrder(values, original, org, false);
    }

    public List<BaseModuleFieldValue> validateOrder(List<BaseModuleFieldValue> values,
                                                   List<BaseModuleFieldValue> original, String org, boolean storeFrozen) {
        var storeField = storeField(org);
        if (storeField == null) return values; // The integration has not enabled store selection yet.
        var result = new ArrayList<>(values == null ? List.<BaseModuleFieldValue>of() : values);
        String selected = store(storeField, result);
        String previous = original == null ? null : store(storeField, original);
        if (original != null && !selected.equals(previous) && (storeFrozen || isStoreLocked(original, org))) {
            throw error("order.store.locked");
        }
        result.removeIf(f -> storeField.getId().equals(f.getFieldId()));
        result.add(new BaseModuleFieldValue(storeField.getId(), selected));
        // A business edit must not erase synchronization state and accidentally unlock the store.
        Set<String> systemIds = new HashSet<>();
        fields(FormKey.ORDER.getKey(), org).stream().filter(f -> f.getInternalKey() != null && SYNC_FIELDS.contains(f.getInternalKey()))
                .forEach(f -> systemIds.add(f.getId()));
        result.removeIf(f -> systemIds.contains(f.getFieldId()));
        if (original != null) original.stream().filter(f -> systemIds.contains(f.getFieldId())).forEach(result::add);

        var table = field(fields(FormKey.ORDER.getKey(), org), "orderProducts");
        if (!(table instanceof SubField sub) || sub.getSubFields() == null) return result;
        Object rows = value(result, table.getId());
        if (original != null && selected.equals(previous)
                && Objects.equals(normalizedRows(rows), normalizedRows(value(original, table.getId())))) return result;
        var productField = field(sub.getSubFields(), "orderProduct");
        if (productField == null || rows == null) return result;
        List<?> parsed;
        try { parsed = rows instanceof List<?> array ? array : JSON.parseArray(rows.toString()); }
        catch (Exception e) { throw error("order.store.product.invalid"); }
        if (parsed == null) throw error("order.store.product.invalid");
        Set<String> ids = new HashSet<>();
        for (Object item : parsed) {
            if (!(item instanceof Map<?, ?> row)) throw error("order.store.product.invalid");
            Object raw = row.get(productField.getBusinessKey() == null ? productField.getId() : productField.getBusinessKey());
            if (raw == null) raw = row.get(productField.getId());
            if (raw instanceof String text && text.startsWith("[")) {
                try { raw = JSON.parseArray(text); } catch (Exception e) { throw error("order.store.product.invalid"); }
            }
            if (raw instanceof List<?> array) raw = array.isEmpty() ? null : array.getFirst();
            if (raw == null || raw.toString().isBlank()) throw error("order.store.product.invalid");
            ids.add(raw.toString());
        }
        if (ids.isEmpty()) return result;
        var stockField = field(fields(FormKey.PRODUCT.getKey(), org), STOCKS);
        if (stockField == null) throw error("order.store.stock.unavailable");
        var records = products.selectByIds(new ArrayList<>(ids));
        if (records.size() != ids.size() || records.stream().anyMatch(p -> !org.equals(p.getOrganizationId()) || !"1".equals(p.getStatus()))) {
            throw error("order.store.product.invalid");
        }
        var metadata = productFields.getResourceFieldMap(new ArrayList<>(ids), true);
        for (String id : ids) {
            var stocks = stocks(value(metadata.get(id), stockField.getId()));
            if (!stocks.containsKey(selected)) throw error("order.store.product.invalid");
            quantity(stocks.get(selected)); // Zero and negative reference stock do not block order creation.
        }
        return result;
    }

    public boolean isStoreLocked(List<BaseModuleFieldValue> values, String org) {
        var config = fields(FormKey.ORDER.getKey(), org);
        var number = field(config, "lingxingOrderNumber");
        if (number != null && value(values, number.getId()) != null && !value(values, number.getId()).toString().isBlank()) return true;
        var status = field(config, "lingxingOrderSyncStatus");
        Object raw = status == null ? null : value(values, status.getId());
        return raw != null && Set.of("同步中", "待人工确认", "成功", "取消中", "已取消", "取消失败").contains(raw.toString());
    }

    private Object normalizedRows(Object rows) {
        if (rows instanceof String text) {
            try { return JSON.parseArray(text); } catch (Exception ignored) { return rows; }
        }
        return rows;
    }

    private Map<String, Object> stocks(Object raw) {
        try { var result = JSON.parseToMap(raw.toString()); if (result == null) throw new IllegalArgumentException(); return result; }
        catch (Exception e) { throw error("order.store.stock.unavailable"); }
    }

    private long quantity(Object value) {
        try {
            if (!(value instanceof Number)) throw new ArithmeticException();
            long number = new BigDecimal(value.toString()).longValueExact();
            if (number > 9007199254740991L || number < -9007199254740991L) throw new ArithmeticException();
            return number;
        } catch (Exception e) { throw error("order.store.stock.unavailable"); }
    }

    private GenericException error(String key) { return new GenericException(Translator.get(key)); }
}
