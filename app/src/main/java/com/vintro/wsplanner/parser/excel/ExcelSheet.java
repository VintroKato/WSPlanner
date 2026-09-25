package com.vintro.wsplanner.parser.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// in-memory sheet containing rows and merged region definitions
public class ExcelSheet {
    private final String name;
    private final List<ExcelRow> rows;
    private final List<ExcelRange> mergedRegions;

    // construct sheet from row-cell map and list of merged regions
    public ExcelSheet(String name, Map<Integer, Map<Integer, String>> rowMap, List<ExcelRange> mergedRegions) {
        this.name = name;
        this.rows = new ArrayList<>();
        if (rowMap != null && !rowMap.isEmpty()) {
            int maxRow = -1;
            for (Integer r : rowMap.keySet()) {
                if (r > maxRow) maxRow = r;
            }
            for (int r = 0; r <= maxRow; r++) {
                Map<Integer, String> cellMap = rowMap.get(r);
                this.rows.add(new ExcelRow(cellMap));
            }
        }
        this.mergedRegions = mergedRegions != null ? mergedRegions : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    // index of the last row in sheet
    public int getLastRowNum() {
        return Math.max(0, rows.size() - 1);
    }

    // get row by 0-based index
    public ExcelRow getRow(int rowIdx) {
        if (rowIdx >= 0 && rowIdx < rows.size()) {
            return rows.get(rowIdx);
        }
        return null;
    }

    // list of merged cell ranges in sheet
    public List<ExcelRange> getMergedRegions() {
        return mergedRegions;
    }
}
