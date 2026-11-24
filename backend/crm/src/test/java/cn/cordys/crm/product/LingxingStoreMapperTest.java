package cn.cordys.crm.product;

import cn.cordys.crm.base.BaseTest;
import cn.cordys.crm.product.dto.request.ProductPageRequest;
import cn.cordys.crm.product.mapper.ExtProductMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@Transactional
class LingxingStoreMapperTest extends BaseTest {
    @Resource JdbcTemplate jdbc;
    @Resource ExtProductMapper mapper;
    @Override protected String getBasePath() { return "/product/"; }

    void product(String id, String org, String status, String stock) {
        jdbc.update("INSERT INTO product(id,organization_id,name,price,status,pos,create_time,update_time,create_user,update_user) VALUES(?,?,?,1,?,1,1,1,'admin','admin')", id, org, id, status);
        if (stock != null) jdbc.update("INSERT INTO product_field_blob(id,resource_id,field_id,field_value) VALUES(?,?,'ls-stock',?)", id + "-stock", id, stock);
    }
    @Test void filtersBeforePaginationAndKeepsZeroStockWithoutLeakingAnotherStoreOrOrganization() {
        product("ls-shared", "ls-org", "1", "{\"store-a\":12,\"store-b\":0}");
        product("ls-a", "ls-org", "1", "{\"store-a\":4}");
        product("ls-b", "ls-org", "1", "{\"store-b\":3}");
        product("ls-invalid", "ls-org", "1", "not-json");
        product("ls-manual", "ls-org", "1", null);
        product("ls-other", "ls-other-org", "1", "{\"store-b\":10}");
        product("ls-down", "ls-org", "2", "{\"store-b\":10}");
        var request = new ProductPageRequest(); request.setStatus("1"); request.setLingxingStockFieldId("ls-stock");
        for (var entry : Map.of("store-a", Set.of("ls-shared", "ls-a"), "store-b", Set.of("ls-shared", "ls-b")).entrySet()) {
            request.setLingxingStoreId(entry.getKey());
            var result = mapper.list(request, "ls-org").stream().map(p -> p.getId()).collect(Collectors.toSet());
            assertEquals(entry.getValue(), result);
        }
        request.setLingxingStoreId("store-b");
        com.github.pagehelper.PageHelper.startPage(1, 1);
        var page = mapper.list(request, "ls-org");
        assertEquals(1, page.size());
        assertEquals(2, ((com.github.pagehelper.Page<?>) page).getTotal());
    }
}
