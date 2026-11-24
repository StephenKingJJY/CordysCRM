package cn.cordys.crm.product;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.product.domain.Product;
import cn.cordys.crm.product.dto.request.ProductPageRequest;
import cn.cordys.crm.product.dto.response.ProductListResponse;
import cn.cordys.crm.product.service.LingxingStoreService;
import cn.cordys.crm.product.service.ProductFieldService;
import cn.cordys.crm.system.dto.field.InputField;
import cn.cordys.crm.system.dto.field.ProductSubField;
import cn.cordys.crm.system.dto.field.SelectField;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.field.base.OptionProp;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LingxingStoreServiceTest {
    ModuleFormCacheService forms = mock(ModuleFormCacheService.class);
    ProductFieldService fields = mock(ProductFieldService.class);
    BaseMapper<Product> products = mock(BaseMapper.class);
    LingxingStoreService service = new LingxingStoreService(forms, fields);
    MessageSource previous;

    BaseField field(String id, String key) {
        var f = new InputField(); f.setId(id); f.setInternalKey(key); return f;
    }
    ModuleFormConfigDTO config(BaseField... fields) {
        var c = new ModuleFormConfigDTO(); c.setFields(List.of(fields)); return c;
    }
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "products", products);
        previous = (MessageSource) ReflectionTestUtils.getField(Translator.class, "messageSource");
        new Translator().setMessageSource(new StaticMessageSource());
        var store = new SelectField(); store.setId("store"); store.setInternalKey("lingxingOrderStore");
        store.setDefaultValue("store-a"); store.setOptions(List.of(new OptionProp("store-a", "A"), new OptionProp("store-b", "B")));
        var table = new ProductSubField(); table.setId("products"); table.setInternalKey("orderProducts");
        table.setSubFields(List.of(field("product", "orderProduct")));
        when(forms.getBusinessFormConfig("order", "org")).thenReturn(config(store, table,
                field("number", "lingxingOrderNumber"), field("sync", "lingxingOrderSyncStatus")));
        when(forms.getBusinessFormConfig("product", "org")).thenReturn(config(
                field("stocks", "lingxingStoreStocks"), field("reference", "lingxingReferenceStock")));
        var product = new Product(); product.setId("p"); product.setOrganizationId("org"); product.setStatus("1");
        when(products.selectByIds(anyList())).thenReturn(List.of(product));
        when(fields.getResourceFieldMap(anyList(), eq(true))).thenReturn(Map.of("p", List.of(
                new BaseModuleFieldValue("stocks", "{\"store-a\":12,\"store-b\":0}"))));
    }
    @AfterEach void restore() { new Translator().setMessageSource(previous); }
    List<BaseModuleFieldValue> order(String store) {
        return List.of(new BaseModuleFieldValue("store", store),
                new BaseModuleFieldValue("products", List.of(Map.of("product", List.of("p")))));
    }
    @Test void productQueryUsesOnlyAuthoritativeStoreOptions() {
        var request = new ProductPageRequest(); request.setLingxingStoreId("store-b");
        service.prepareProductQuery(request, "org"); assertEquals("stocks", request.getLingxingStockFieldId());
        request.setLingxingStoreId("other"); assertThrows(GenericException.class, () -> service.prepareProductQuery(request, "org"));
        request.setLingxingStoreId("store-b\".*"); assertThrows(GenericException.class, () -> service.prepareProductQuery(request, "org"));
    }
    @Test void selectedStoreGetsItsOwnZeroRatherThanDefaultStock() {
        var product = new ProductListResponse(); product.setId("p"); product.setModuleFields(new ArrayList<>(List.of(
                new BaseModuleFieldValue("stocks", "{\"store-a\":12,\"store-b\":0}"),
                new BaseModuleFieldValue("reference", 12))));
        service.applyReferenceStock(List.of(product), "store-b", "org");
        assertEquals(0L, product.getModuleFields().getFirst().getFieldValue());
        assertTrue(product.getModuleFields().stream().noneMatch(f -> f.getFieldId().equals("stocks")));
    }
    @Test void legacyOrdersKeepDefaultStoreAndZeroStockDoesNotBlock() {
        var result = service.validateOrder(order(null), null, "org");
        assertEquals("store-a", result.stream().filter(f -> f.getFieldId().equals("store")).findFirst().orElseThrow().getFieldValue());
        assertDoesNotThrow(() -> service.validateOrder(order("store-b"), null, "org"));
    }
    @Test void blocksProductsFromAnotherStoreAndUnknownInventory() {
        when(fields.getResourceFieldMap(anyList(), eq(true))).thenReturn(Map.of("p", List.of(new BaseModuleFieldValue("stocks", "{\"store-a\":12}"))));
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-b"), null, "org"));
        when(fields.getResourceFieldMap(anyList(), eq(true))).thenReturn(Map.of());
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-a"), null, "org"));
    }
    @Test void blocksCrossOrganizationAndInactiveProducts() {
        var p = new Product(); p.setOrganizationId("other"); p.setStatus("1"); when(products.selectByIds(anyList())).thenReturn(List.of(p));
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-a"), null, "org"));
        p.setOrganizationId("org"); p.setStatus("2");
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-a"), null, "org"));
    }
    @Test void locksStoreDuringUncertainAndSuccessfulSynchronization() {
        for (String status : List.of("同步中", "待人工确认", "成功", "取消中", "已取消", "取消失败")) {
            var original = new ArrayList<>(order("store-a")); original.add(new BaseModuleFieldValue("sync", status));
            assertThrows(GenericException.class, () -> service.validateOrder(order("store-b"), original, "org"));
        }
        var original = new ArrayList<>(order("store-a")); original.add(new BaseModuleFieldValue("number", "LX"));
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-b"), original, "org"));
    }
    @Test void allowsAnUnsentOrderToChangeStoreButKeepsHistoricalProductsWhenUnchanged() {
        assertDoesNotThrow(() -> service.validateOrder(order("store-b"), order("store-a"), "org"));
        when(products.selectByIds(anyList())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.validateOrder(order("store-a"), order("store-a"), "org"));
    }
    @Test void malformedStockAndFractionsFailInsteadOfBecomingZero() {
        for (String stock : List.of("null", "broken", "{\"store-b\":null}", "{\"store-b\":0.5}", "{\"store-b\":\"7\"}")) {
            when(fields.getResourceFieldMap(anyList(), eq(true))).thenReturn(Map.of("p", List.of(new BaseModuleFieldValue("stocks", stock))));
            assertThrows(GenericException.class, () -> service.validateOrder(order("store-b"), null, "org"));
        }
    }
    @Test void businessEditsPreserveSyncMetadataAndCannotManufactureItOnCreate() {
        var original = new ArrayList<>(order("store-a")); original.add(new BaseModuleFieldValue("number", "LX"));
        original.add(new BaseModuleFieldValue("sync", "成功"));
        var edited = new ArrayList<>(order("store-a")); edited.add(new BaseModuleFieldValue("sync", ""));
        var result = service.validateOrder(edited, original, "org");
        assertTrue(result.contains(new BaseModuleFieldValue("number", "LX")));
        assertTrue(result.contains(new BaseModuleFieldValue("sync", "成功")));
        var created = service.validateOrder(original, null, "org");
        assertTrue(created.stream().noneMatch(f -> Set.of("sync", "number").contains(f.getFieldId())));
    }
    @Test void historicalFulfillmentStagesRemainLockedEvenIfOldMetadataIsMissing() {
        assertThrows(GenericException.class, () -> service.validateOrder(order("store-b"), order("store-a"), "org", true));
    }
}
