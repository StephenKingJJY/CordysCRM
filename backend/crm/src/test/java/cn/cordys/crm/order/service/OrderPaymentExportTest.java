package cn.cordys.crm.order.service;

import cn.cordys.common.dto.*;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.service.BaseExportService;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.approval.service.ApprovalFlowService;
import cn.cordys.crm.order.dto.request.OrderPageRequest;
import cn.cordys.crm.order.dto.response.*;
import cn.cordys.crm.order.mapper.*;
import cn.cordys.crm.system.domain.ExportTask;
import cn.cordys.crm.system.excel.domain.MergeResult;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.registry.ExportThreadRegistry;
import com.github.pagehelper.PageHelper;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPaymentExportTest {
    @TempDir Path temp;
    ExtOrderPaymentMapper payments;
    OrderPaymentExportService paymentExport;
    ExtOrderMapper orders;
    OrderService orderService;
    ApprovalFlowService approval;
    ModuleFormService form;
    TestExporter exporter;
    MockedStatic<CommonBeanFactory> beans;

    @BeforeEach void setUp() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/cordys-crm"); messages.setDefaultEncoding("UTF-8");
        new Translator().setMessageSource(messages);
        payments = mock(ExtOrderPaymentMapper.class);
        paymentExport = new OrderPaymentExportService(payments);
        orders = mock(ExtOrderMapper.class); orderService = mock(OrderService.class);
        approval = mock(ApprovalFlowService.class); form = mock(ModuleFormService.class);
        exporter = new TestExporter(temp.resolve("orders.xlsx").toFile());
        ReflectionTestUtils.setField(exporter, "paymentExportService", paymentExport);
        ReflectionTestUtils.setField(exporter, "extOrderMapper", orders);
        ReflectionTestUtils.setField(exporter, "orderService", orderService);
        ReflectionTestUtils.setField(exporter, "approvalFlowService", approval);
        var stages = mock(ExtOrderStageConfigMapper.class);
        ReflectionTestUtils.setField(exporter, "extOrderStageConfigMapper", stages);
        when(stages.getStageConfigList(anyString())).thenReturn(List.of());
        when(orderService.buildList(anyList(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(approval.filterResourcesWithExportPermission(anyString(), anyList(), anyString(), any(), any()))
                .thenAnswer(i -> ((List<OrderListResponse>) i.getArgument(1)).stream().map(OrderListResponse::getId).toList());
        when(form.getAllExportHeads(anyList(), anyString(), anyString()))
                .thenReturn(List.of(List.of("订单编号"), List.of("订单名称"), List.of("订单金额")));
        when(form.getExportMergeHeads(anyString(), anyString(), anyList())).thenReturn(List.of("number", "name", "amount"));
        beans = mockStatic(CommonBeanFactory.class);
        beans.when(() -> CommonBeanFactory.getBean(ModuleFormService.class)).thenReturn(form);
    }

    @AfterEach void tearDown() { beans.close(); PageHelper.clearPage(); }

    @Test void selectedWorkbookContainsScopedSummaryAndAllPaymentHistory() throws Exception {
        var a = order("a", "100"); var hidden = order("hidden", "500");
        when(orders.getListByIds(anyList(), anyString(), anyString(), any())).thenReturn(List.of(a, hidden));
        when(approval.filterResourcesWithExportPermission(anyString(), anyList(), anyString(), any(), any())).thenReturn(List.of("a"));
        var first = payment("p1", "a", "20.10", "2026-09-19", false);
        var second = payment("p2", "a", "30.20", "2026-09-20", false);
        var voided = payment("p3", "a", "99", "2026-09-21", true);
        first.setRemark("=HYPERLINK(\"https://example.invalid\")");
        when(payments.listForExport(List.of("a"), "org")).thenReturn(List.of(first, second, voided));
        when(payments.receiptsForExport(List.of("a"), "org")).thenReturn(List.of(
                new OrderPaymentExportReceipt("p1", "receipt-1.pdf"), new OrderPaymentExportReceipt("p1", "receipt-2.png")));
        exporter.exportSelectWithMergeStrategy(request(List.of("a", "hidden"), Locale.SIMPLIFIED_CHINESE));
        try (Workbook wb = WorkbookFactory.create(exporter.file)) {
            assertEquals(2, wb.getNumberOfSheets());
            Sheet main = wb.getSheet("订单"), details = wb.getSheet("回款明细");
            assertNotNull(main); assertNotNull(details);
            assertEquals(1, main.getLastRowNum());
            Row row = main.getRow(1);
            assertEquals("000-a", row.getCell(0).getStringCellValue());
            assertEquals(50.30, row.getCell(3).getNumericCellValue(), .000001);
            assertEquals(49.70, row.getCell(4).getNumericCellValue(), .000001);
            assertEquals("部分回款", row.getCell(5).getStringCellValue());
            assertEquals(LocalDate.of(2026, 9, 20), row.getCell(6).getLocalDateTimeCellValue().toLocalDate());
            assertTrue(DateUtil.isCellDateFormatted(row.getCell(6)));
            assertTrue(DateUtil.isCellDateFormatted(details.getRow(1).getCell(2)));
            assertEquals("#,##0.00", row.getCell(3).getCellStyle().getDataFormatString());
            assertEquals(3, details.getLastRowNum());
            assertEquals(CellType.NUMERIC, details.getRow(1).getCell(3).getCellType());
            assertEquals(CellType.STRING, details.getRow(1).getCell(4).getCellType());
            assertEquals(first.getRemark(), details.getRow(1).getCell(4).getStringCellValue());
            assertEquals("receipt-1.pdf\nreceipt-2.png", details.getRow(1).getCell(11).getStringCellValue());
            assertEquals("已作废", details.getRow(3).getCell(7).getStringCellValue());
            assertEquals("Correction", details.getRow(3).getCell(8).getStringCellValue());
            assertEquals("p3", details.getRow(3).getCell(12).getStringCellValue());
        }
        verify(payments).listForExport(List.of("a"), "org");
        verify(payments).receiptsForExport(List.of("a"), "org");
        if (System.getProperty("payment.export.sample") != null) {
            first.setRemark("第一笔到账，按本订单分摊");
            second.setRemark("第二笔到账");
            exporter.exportSelectWithMergeStrategy(request(List.of("a", "hidden"), Locale.SIMPLIFIED_CHINESE));
            Files.copy(exporter.file.toPath(), Path.of(System.getProperty("payment.export.sample")), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test void exportsAllPagesEvenIfFirstPageIsEntirelyPermissionFiltered() throws Exception {
        var blocked = order("blocked", "1"); var allowed = order("allowed", "10");
        when(orders.list(any(), anyString(), anyString(), any(), eq(false)))
                .thenReturn(Collections.nCopies(BaseExportService.EXPORT_MAX_COUNT, blocked), List.of(allowed));
        when(approval.filterResourcesWithExportPermission(anyString(), anyList(), anyString(), any(), any()))
                .thenReturn(List.of(), List.of("allowed"));
        exporter.exportAllWithMergeStrategy(request(null, Locale.SIMPLIFIED_CHINESE));
        try (Workbook wb = WorkbookFactory.create(exporter.file)) {
            assertEquals(1, wb.getSheetAt(0).getLastRowNum());
            assertEquals("000-allowed", wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals(0, wb.getSheetAt(1).getLastRowNum());
            assertEquals(13, wb.getSheetAt(1).getRow(0).getLastCellNum());
        }
        verify(orders, times(2)).list(any(), anyString(), anyString(), any(), eq(false));
        verify(payments).listForExport(List.of("allowed"), "org");
    }

    @Test void noOrdersStillProducesBothHeaderOnlySheets() throws Exception {
        exporter.exportAllWithMergeStrategy(request(null, Locale.US));
        try (Workbook wb = WorkbookFactory.create(exporter.file)) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("Orders", wb.getSheetName(0));
            assertEquals("Payment details", wb.getSheetName(1));
            assertEquals("Total received", wb.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
            assertEquals(0, wb.getSheetAt(0).getLastRowNum());
            assertEquals(0, wb.getSheetAt(1).getLastRowNum());
        }
        verifyNoInteractions(payments);
    }

    @Test void extraProductRowsDoNotDuplicatePaymentAmountAndMergeUsesHeaderDepth() throws Exception {
        exporter.productRows = true;
        when(form.getAllExportHeads(anyList(), anyString(), anyString()))
                .thenReturn(List.of(List.of("订单编号"), List.of("产品", "名称"), List.of("产品", "金额")));
        var a = order("a", "100");
        when(orders.getListByIds(anyList(), anyString(), anyString(), any())).thenReturn(List.of(a));
        when(payments.listForExport(anyList(), anyString())).thenReturn(List.of(payment("p", "a", "20", "2026-09-20", false)));
        exporter.exportSelectWithMergeStrategy(request(List.of("a"), Locale.SIMPLIFIED_CHINESE));
        try (Workbook wb = WorkbookFactory.create(exporter.file)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(20, sheet.getRow(2).getCell(3).getNumericCellValue(), .000001);
            assertTrue(sheet.getRow(3).getCell(3) == null || sheet.getRow(3).getCell(3).getCellType() == CellType.BLANK
                    || sheet.getRow(3).getCell(3).toString().isEmpty());
            assertTrue(sheet.getMergedRegions().stream().anyMatch(region -> region.getFirstColumn() == 3
                    && region.getFirstRow() == 2 && region.getLastRow() == 3));
            assertEquals(1, wb.getSheetAt(1).getLastRowNum());
        }
    }

    @Test void selectedBatchesAppendToSameTwoSheetsWithoutRepeatingHeads() throws Exception {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 501).mapToObj(i -> "order-" + i).toList();
        when(orders.getListByIds(anyList(), anyString(), anyString(), any())).thenAnswer(invocation ->
                ((List<String>) invocation.getArgument(0)).stream().map(id -> order(id, "100")).toList());
        when(payments.listForExport(anyList(), anyString())).thenAnswer(invocation ->
                ((List<String>) invocation.getArgument(0)).stream()
                        .map(id -> payment("p-" + id, id, "20", "2026-09-20", false)).toList());
        exporter.exportSelectWithMergeStrategy(request(ids, Locale.SIMPLIFIED_CHINESE));
        try (Workbook wb = WorkbookFactory.create(exporter.file)) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals(501, wb.getSheetAt(0).getLastRowNum());
            assertEquals(501, wb.getSheetAt(1).getLastRowNum());
            assertEquals("000-order-501", wb.getSheetAt(0).getRow(501).getCell(0).getStringCellValue());
            assertEquals("p-order-501", wb.getSheetAt(1).getRow(501).getCell(12).getStringCellValue());
            assertEquals(20, wb.getSheetAt(0).getRow(501).getCell(3).getNumericCellValue(), .000001);
        }
        verify(payments, times(2)).listForExport(anyList(), eq("org"));
    }

    @Test void ordinaryExporterRetainsSingleSheetAndSingleHeaderMergeOffsets() throws Exception {
        File file = temp.resolve("ordinary.xlsx").toFile();
        BaseExportService ordinary = new BaseExportService() {
            @Override public File prepareExportFile(String id, String name, String org) { return file; }
        };
        var task = new ExportTask(); task.setId("ordinary"); task.setOrganizationId("org");
        ExportThreadRegistry.register("ordinary", Thread.currentThread());
        try {
            ordinary.batchHandleDataWithMergeStrategy(List.of(List.of("Order"), List.of("Value")), task,
                    "ordinary", List.of(0), new OrderPageRequest(), request -> MergeResult.builder()
                            .dataList(List.of(List.of("A", 1), List.of("", 2)))
                            .mergeRegions(List.of(new int[]{0, 1})).handleCount(1).queryCount(1).build());
        } finally { ExportThreadRegistry.remove("ordinary"); }
        try (Workbook wb = WorkbookFactory.create(file)) {
            assertEquals(1, wb.getNumberOfSheets());
            assertEquals("导出数据", wb.getSheetName(0));
            assertEquals("A", wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("A2:A3", wb.getSheetAt(0).getMergedRegion(0).formatAsString());
        }
    }

    @Test void summariesCoverNoPaymentsOverpaymentAndAllVoided() {
        var over = payment("p", "over", "12.34", "2026-09-20", false);
        var voided = payment("v", "voided", "50", "2026-09-21", true);
        when(payments.listForExport(anyList(), anyString())).thenReturn(List.of(over, voided));
        var data = paymentExport.load(List.of(order("over", "10"), order("empty", "100"), order("voided", "100")), "org", Locale.US);
        assertEquals(List.of(new BigDecimal("12.34"), new BigDecimal("0"), "Overpaid", "2026-09-20"), data.summaries().get("over"));
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("100.00"), "Unpaid", ""), data.summaries().get("empty"));
        assertEquals("", data.summaries().get("voided").get(3));
        assertEquals(2, data.details().size());
    }

    private ExportDTO request(List<String> ids, Locale locale) {
        var page = new OrderPageRequest(); page.setCurrent(1); page.setPageSize(20);
        return ExportDTO.builder().orgId("org").userId("user").formKey("ORDER").locale(locale).fileName("orders")
                .headList(List.of(new ExportHeadDTO("number", "订单编号", null), new ExportHeadDTO("name", "订单名称", null),
                        new ExportHeadDTO("amount", "订单金额", null)))
                .selectIds(ids).pageRequest(page).build();
    }

    static OrderListResponse order(String id, String amount) {
        var order = new OrderListResponse(); order.setId(id); order.setNumber("000-" + id); order.setName("示例订单 " + id);
        order.setAmount(new BigDecimal(amount)); order.setCreateTime(1L); order.setUpdateTime(1L); order.setModuleFields(List.of());
        return order;
    }

    static OrderPaymentResponse payment(String id, String orderId, String amount, String date, boolean voided) {
        var p = new OrderPaymentResponse(); p.setId(id); p.setOrderId(orderId); p.setAmount(new BigDecimal(amount));
        p.setReceivedDate(LocalDate.parse(date)); p.setVoided(voided); p.setCreateUserName("Finance"); p.setCreateTime(1789952400000L);
        if (voided) { p.setVoidReason("Correction"); p.setVoidUserName("Finance"); p.setVoidTime(1790038800000L); }
        return p;
    }

    static class TestExporter extends OrderExportService {
        final File file;
        boolean productRows;
        TestExporter(File file) { this.file = file; }
        @Override public File prepareExportFile(String id, String name, String org) { return file; }
        @Override protected ExportFieldParam getExportFieldParam(ExportDTO request) {
            var amount = new cn.cordys.crm.system.dto.field.InputNumberField();
            amount.setId("amount"); amount.setBusinessKey("amount"); amount.setType("INPUT_NUMBER");
            amount.setPrecision(2); amount.setDecimalPlaces(true); amount.setShowThousandsSeparator(false);
            return ExportFieldParam.builder().fieldConfigMap(Map.of("amount", amount)).subIds(Set.of()).build();
        }
        @Override protected List<List<Object>> buildDataWithSub(List<BaseModuleFieldValue> fields, ExportFieldParam params,
                List<FieldExportMeta> metas, LinkedHashMap<String, Object> system, Map<Object, Object> cache) {
            if (productRows) return List.of(List.of(system.get("number"), "Product A", 40), List.of("", "Product B", 60));
            return super.buildDataWithSub(fields, params, metas, system, cache);
        }
        @Override public String asyncExport(String name, String org, String user, Locale locale, String module,
                String type, cn.cordys.common.service.ExportExecutor executor) {
            var task = new ExportTask(); task.setId("task"); task.setFileId("file"); task.setOrganizationId(org);
            ExportThreadRegistry.register("task", Thread.currentThread());
            try { executor.execute(task); } catch (Exception e) { throw new RuntimeException(e); }
            finally { ExportThreadRegistry.remove("task"); }
            return "task";
        }
    }
}
