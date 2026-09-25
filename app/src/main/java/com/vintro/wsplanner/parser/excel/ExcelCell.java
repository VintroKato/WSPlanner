package com.vintro.wsplanner.parser.excel;

// lightweight in-memory excel cell representation
public class ExcelCell {
    private final String text;

    public ExcelCell(String text) {
        this.text = text != null ? text.trim() : "";
    }

    // return raw cell text value
    public String asString() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
