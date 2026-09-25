package com.vintro.wsplanner.parser.excel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// lightweight in-memory excel row containing indexed cells
public class ExcelRow implements Iterable<ExcelCell> {
    private final List<ExcelCell> cells;

    // construct row from sparse column index map
    public ExcelRow(Map<Integer, String> cellMap) {
        this.cells = new ArrayList<>();
        if (cellMap != null && !cellMap.isEmpty()) {
            int maxCol = -1;
            for (Integer c : cellMap.keySet()) {
                if (c > maxCol) maxCol = c;
            }
            for (int i = 0; i <= maxCol; i++) {
                String val = cellMap.get(i);
                cells.add(new ExcelCell(val != null ? val : ""));
            }
        }
    }

    // get cell by 0-based column index
    public ExcelCell getCell(int colIdx) {
        if (colIdx >= 0 && colIdx < cells.size()) {
            return cells.get(colIdx);
        }
        return null;
    }

    // total number of populated cells in row
    public int getLastCellNum() {
        return cells.size();
    }

    @Override
    public Iterator<ExcelCell> iterator() {
        return cells.iterator();
    }
}
