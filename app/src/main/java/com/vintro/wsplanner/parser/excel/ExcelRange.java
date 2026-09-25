package com.vintro.wsplanner.parser.excel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 2D cell range coordinate representing merged regions (e.g. A1:C3)
public class ExcelRange {
    private static final Pattern CELL_PATTERN = Pattern.compile("([A-Za-z]+)(\\d+)");

    private final int firstRow;
    private final int lastRow;
    private final int firstColumn;
    private final int lastColumn;

    public ExcelRange(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        this.firstRow = firstRow;
        this.lastRow = lastRow;
        this.firstColumn = firstColumn;
        this.lastColumn = lastColumn;
    }

    public int getFirstRow() {
        return firstRow;
    }

    public int getLastRow() {
        return lastRow;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public int getLastColumn() {
        return lastColumn;
    }

    // check if row and column coordinates fall inside this range
    public boolean isInRange(int rowIdx, int colIdx) {
        return rowIdx >= firstRow && rowIdx <= lastRow && colIdx >= firstColumn && colIdx <= lastColumn;
    }

    // format range as excel reference string (e.g. A1:B2)
    public String formatAsString() {
        return indexToColName(firstColumn) + (firstRow + 1) + ":" + indexToColName(lastColumn) + (lastRow + 1);
    }

    // parse excel reference string (e.g. A1:B2) into ExcelRange
    public static ExcelRange parse(String ref) {
        if (ref == null || ref.trim().isEmpty()) return null;
        String[] parts = ref.trim().split(":");
        Matcher m1 = CELL_PATTERN.matcher(parts[0]);
        if (!m1.matches()) return null;
        int c1 = colNameToIndex(m1.group(1));
        int r1 = Integer.parseInt(m1.group(2)) - 1;

        if (parts.length > 1) {
            Matcher m2 = CELL_PATTERN.matcher(parts[1]);
            if (!m2.matches()) return null;
            int c2 = colNameToIndex(m2.group(1));
            int r2 = Integer.parseInt(m2.group(2)) - 1;
            return new ExcelRange(Math.min(r1, r2), Math.max(r1, r2), Math.min(c1, c2), Math.max(c1, c2));
        } else {
            return new ExcelRange(r1, r1, c1, c1);
        }
    }

    // convert column letters to 0-based index (e.g. A -> 0, Z -> 25, AA -> 26)
    private static int colNameToIndex(String col) {
        int result = 0;
        for (int i = 0; i < col.length(); i++) {
            result = result * 26 + (Character.toUpperCase(col.charAt(i)) - 'A' + 1);
        }
        return result - 1;
    }

    // convert 0-based column index to column letters (e.g. 0 -> A, 26 -> AA)
    private static String indexToColName(int colIdx) {
        StringBuilder sb = new StringBuilder();
        colIdx++;
        while (colIdx > 0) {
            int rem = (colIdx - 1) % 26;
            sb.append((char) ('A' + rem));
            colIdx = (colIdx - 1) / 26;
        }
        return sb.reverse().toString();
    }
}
