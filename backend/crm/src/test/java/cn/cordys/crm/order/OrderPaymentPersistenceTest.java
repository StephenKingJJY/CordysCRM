package cn.cordys.crm.order;

import cn.cordys.crm.order.dto.response.OrderPaymentResponse;
import cn.cordys.crm.order.mapper.ExtOrderPaymentMapper;
import cn.cordys.crm.order.service.OrderPaymentService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class OrderPaymentPersistenceTest {
    @Test void migrationAndMapperPreservePaymentHistoryInMysql() throws Exception {
        try (var mysql = new GenericContainer<>("mysql:8.4")
                .withEnv("MYSQL_ROOT_PASSWORD", "local-payment-test")
                .withEnv("MYSQL_DATABASE", "payment_test")
                .withExposedPorts(3306)
                .waitingFor(Wait.forLogMessage(".*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(3)))) {
            mysql.start();
            var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                    "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                            + "/payment_test?allowPublicKeyRetrieval=true&useSSL=false", "root", "local-payment-test");
            try (var connection = ds.getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE sales_order (id VARCHAR(32) PRIMARY KEY,organization_id VARCHAR(32),amount DECIMAL(20,10)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
                sql.execute("CREATE TABLE sys_user (id VARCHAR(32) PRIMARY KEY,name VARCHAR(255)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
                sql.execute("CREATE TABLE sys_attachment (id VARCHAR(32),name VARCHAR(255),resource_id VARCHAR(32),organization_id VARCHAR(32),create_time BIGINT) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
                var runner = new ScriptRunner(connection); runner.setLogWriter(null); runner.setStopOnError(true);
                try (var input = getClass().getResourceAsStream("/migration/1.9.1/ddl/V1.9.1_3__order_payments.sql")) {
                    assertNotNull(input); runner.runScript(new InputStreamReader(input, StandardCharsets.UTF_8));
                }
                sql.execute("INSERT INTO sales_order VALUES ('a','org',10000),('b','other-org',10)");
                sql.execute("INSERT INTO sys_user VALUES ('u','Finance')");
                connection.commit();
            }
            var config = new Configuration(new Environment("payment-test", new JdbcTransactionFactory(), ds));
            config.setMapUnderscoreToCamelCase(true); config.addMapper(ExtOrderPaymentMapper.class);
            try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
                var mapper = session.getMapper(ExtOrderPaymentMapper.class);
                assertNotNull(mapper.getOrder("a", "org")); assertNull(mapper.getOrder("a", "other-org"));
                var p = new OrderPaymentResponse(); p.setId("p1"); p.setOrderId("a"); p.setOrganizationId("org");
                p.setAmount(new BigDecimal("6000.25")); p.setReceivedDate(LocalDate.of(2026,9,21));
                p.setRemark("Allocated from shared transfer"); p.setCreateUser("u"); p.setCreateTime(1L);
                assertEquals(1, mapper.insert(p));
                var stored = mapper.list("a", "org"); assertEquals(1, stored.size());
                assertEquals("Finance", stored.getFirst().getCreateUserName());
                assertEquals(p.getReceivedDate(), stored.getFirst().getReceivedDate());
                assertEquals(p.getAmount(), stored.getFirst().getAmount());
                assertTrue(mapper.list("a", "other-org").isEmpty());
                assertThrows(Exception.class, () -> mapper.insert(p), "Idempotency key must be unique");
                try (var c = ds.getConnection(); var sql = c.createStatement()) {
                    sql.execute("INSERT INTO sys_attachment VALUES ('f1','receipt.pdf','p1','org',1)");
                    assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM sales_order WHERE id='a'"));
                }
                var receipts = mapper.receipts("p1", "a", "org"); assertEquals(1, receipts.size());
                assertEquals("receipt.pdf", receipts.getFirst().name());
                assertTrue(mapper.receipts("p1", "b", "org").isEmpty());
                assertEquals(1, mapper.voidPayment("p1", "a", "org", "u", 2, "Incorrect allocation"));
                assertEquals(0, mapper.voidPayment("p1", "a", "org", "u", 3, "Second void"));
                var voided = mapper.list("a", "org").getFirst(); assertTrue(voided.isVoided());
                assertEquals("Incorrect allocation", voided.getVoidReason()); assertEquals("Finance", voided.getVoidUserName());
                assertEquals("UNPAID", OrderPaymentService.summarize(new BigDecimal("10000"), mapper.list("a", "org")).status());
                assertEquals(1, mapper.count("a")); assertEquals(1, mapper.receipts("p1", "a", "org").size());
            }
        }
    }
}
