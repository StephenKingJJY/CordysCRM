package cn.cordys.crm.order.service;

import cn.cordys.common.util.TimeUtils;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.order.dto.response.OrderListResponse;
import cn.cordys.crm.order.dto.response.OrderPaymentExportReceipt;
import cn.cordys.crm.order.dto.response.OrderPaymentResponse;
import cn.cordys.crm.order.mapper.ExtOrderPaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderPaymentExportService {
    private final ExtOrderPaymentMapper mapper;

    public record Batch(Map<String, List<Object>> summaries, List<List<Object>> details) {}

    /** Orders must already have passed the ordinary order export's data and approval permissions. */
    public Batch load(List<OrderListResponse> orders, String orgId, Locale locale) {
        if (orders.isEmpty()) return new Batch(Map.of(), List.of());
        List<String> ids = orders.stream().map(OrderListResponse::getId).toList();
        Map<String, List<OrderPaymentResponse>> byOrder = mapper.listForExport(ids, orgId).stream()
                .collect(Collectors.groupingBy(OrderPaymentResponse::getOrderId));
        Map<String, String> receipts = mapper.receiptsForExport(ids, orgId).stream()
                .collect(Collectors.groupingBy(OrderPaymentExportReceipt::paymentId,
                        Collectors.mapping(OrderPaymentExportReceipt::name, Collectors.joining("\n"))));
        Map<String, List<Object>> summaries = new LinkedHashMap<>();
        List<List<Object>> details = new ArrayList<>();
        for (OrderListResponse order : orders) {
            List<OrderPaymentResponse> records = byOrder.getOrDefault(order.getId(), List.of());
            var summary = OrderPaymentService.summarize(order.getAmount(), records);
            String latestDate = records.stream().filter(p -> !p.isVoided()).map(OrderPaymentResponse::getReceivedDate)
                    .filter(Objects::nonNull).max(LocalDate::compareTo).map(LocalDate::toString).orElse("");
            summaries.put(order.getId(), List.of(summary.receivedAmount(), summary.remainingAmount(),
                    label("status." + summary.status().toLowerCase(Locale.ROOT), locale), latestDate));
            for (OrderPaymentResponse payment : records) {
                details.add(List.of(text(order.getNumber()), text(order.getName()), payment.getReceivedDate().toString(),
                        payment.getAmount(), text(payment.getRemark()), text(payment.getCreateUserName()),
                        dateTime(payment.getCreateTime()), label(payment.isVoided() ? "voided" : "valid", locale),
                        text(payment.getVoidReason()), text(payment.getVoidUserName()), dateTime(payment.getVoidTime()),
                        receipts.getOrDefault(payment.getId(), ""), payment.getId()));
            }
        }
        return new Batch(summaries, details);
    }

    public List<List<Object>> appendSummary(List<List<Object>> rows, List<Object> summary) {
        List<List<Object>> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = new ArrayList<>(rows.get(i));
            // One amount per order, even when product lines expand it to several spreadsheet rows.
            row.addAll(i == 0 ? summary : Collections.nCopies(summary.size(), ""));
            result.add(row);
        }
        return result;
    }

    public List<List<String>> summaryHeads(Locale locale) {
        return heads(locale, List.of("received", "remaining", "status", "latestDate"));
    }

    public List<List<String>> detailHeads(Locale locale) {
        return heads(locale, List.of("orderNumber", "orderName", "receivedDate", "amount", "remark", "createdBy",
                "createdAt", "recordStatus", "voidReason", "voidedBy", "voidedAt", "receipts", "paymentId"));
    }

    private List<List<String>> heads(Locale locale, List<String> keys) {
        return keys.stream().map(key -> List.of(label(key, locale))).toList();
    }

    static String label(String key, Locale locale) {
        return Translator.get("order.payment.export." + key, locale);
    }

    private String text(String value) { return Objects.toString(value, ""); }
    private String dateTime(Long time) { return time == null ? "" : TimeUtils.getDateTimeStr(time); }
}
