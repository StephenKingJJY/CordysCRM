package cn.cordys.crm.order.excel;

import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteTableHolder;
import org.apache.poi.ss.usermodel.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Styles only the added payment columns, preserving the existing order export. */
public class OrderPaymentExportStyle implements CellWriteHandler {
    private final boolean details;
    private final int summaryStart;
    private final Map<String, CellStyle> styles = new HashMap<>();
    private static final int[] DETAIL_WIDTHS = {24, 32, 16, 22, 44, 20, 24, 16, 44, 20, 24, 40, 36};

    public OrderPaymentExportStyle(boolean details, int summaryStart) {
        this.details = details;
        this.summaryStart = summaryStart;
    }

    @Override public int order() { return 10000; }

    @Override
    public void afterCellDispose(WriteSheetHolder holder, WriteTableHolder table, List<WriteCellData<?>> data,
            Cell cell, Head head, Integer relativeRowIndex, Boolean isHead) {
        int col = cell.getColumnIndex();
        if (!details && col < summaryStart) return;
        int width = details ? DETAIL_WIDTHS[col] : 24;
        cell.getSheet().setColumnWidth(col, width * 256);
        if (Boolean.TRUE.equals(isHead)) return;
        String format = "";
        boolean money = details ? col == 3 : col == summaryStart || col == summaryStart + 1;
        boolean date = details ? col == 2 : col == summaryStart + 3;
        boolean time = details && (col == 6 || col == 10);
        if (money && cell.getCellType() == CellType.NUMERIC) format = "#,##0.00";
        if ((date || time) && cell.getCellType() == CellType.STRING && !cell.getStringCellValue().isBlank()) {
            String text = cell.getStringCellValue();
            if (date) cell.setCellValue(LocalDate.parse(text));
            else cell.setCellValue(LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            format = date ? "yyyy-mm-dd" : "yyyy-mm-dd hh:mm:ss";
        }
        final String numberFormat = format;
        String key = cell.getCellStyle().getIndex() + ":" + format + ":" + money;
        CellStyle style = styles.computeIfAbsent(key, ignored -> {
            CellStyle created = cell.getSheet().getWorkbook().createCellStyle();
            created.cloneStyleFrom(cell.getCellStyle());
            created.setWrapText(true);
            created.setVerticalAlignment(VerticalAlignment.TOP);
            created.setAlignment(money ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
            if (!numberFormat.isEmpty()) created.setDataFormat(cell.getSheet().getWorkbook().createDataFormat().getFormat(numberFormat));
            return created;
        });
        cell.setCellStyle(style);
        if (cell.getCellType() == CellType.STRING) {
            int lines = 0;
            for (String line : cell.getStringCellValue().split("\\n", -1)) {
                int length = line.codePoints().map(c -> c > 255 ? 2 : 1).sum();
                lines += Math.max(1, (int) Math.ceil((double) length / Math.max(1, width - 2)));
            }
            cell.getRow().setHeightInPoints(Math.max(cell.getRow().getHeightInPoints(), Math.min(409, lines * 16f)));
        }
    }
}
