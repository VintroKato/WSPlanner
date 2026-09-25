package com.vintro.wsplanner.parser.excel;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// lightweight zero-dependency xlsx workbook parser using zipinputstream and sax
public class ExcelWorkbook implements Closeable {
    private static final Pattern SHEET_FILE_PATTERN = Pattern.compile("xl/worksheets/sheet(\\d+)\\.xml");
    private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Za-z]+)(\\d+)");

    private final List<ExcelSheet> sheets;

    private ExcelWorkbook(List<ExcelSheet> sheets) {
        this.sheets = sheets;
    }

    // parse xlsx input stream into in-memory workbook
    public static ExcelWorkbook create(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] xlsxBytes = baos.toByteArray();

        byte[] sharedStringsBytes = null;
        Map<Integer, byte[]> sheetXmlBytes = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("xl/sharedStrings.xml".equals(name)) {
                    sharedStringsBytes = readEntry(zis);
                } else {
                    Matcher m = SHEET_FILE_PATTERN.matcher(name);
                    if (m.matches()) {
                        int sheetIndex = Integer.parseInt(m.group(1)) - 1;
                        sheetXmlBytes.put(sheetIndex, readEntry(zis));
                    }
                }
            }
        }

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            SAXParser saxParser = factory.newSAXParser();

            List<String> sharedStrings = new ArrayList<>();
            if (sharedStringsBytes != null) {
                SharedStringsHandler sstHandler = new SharedStringsHandler();
                saxParser.parse(new ByteArrayInputStream(sharedStringsBytes), sstHandler);
                sharedStrings = sstHandler.getStrings();
            }

            List<ExcelSheet> resultSheets = new ArrayList<>();
            int maxSheetIndex = -1;
            for (Integer idx : sheetXmlBytes.keySet()) {
                if (idx > maxSheetIndex) maxSheetIndex = idx;
            }

            for (int i = 0; i <= maxSheetIndex; i++) {
                byte[] sBytes = sheetXmlBytes.get(i);
                if (sBytes != null) {
                    SheetHandler sheetHandler = new SheetHandler(sharedStrings);
                    saxParser.parse(new ByteArrayInputStream(sBytes), sheetHandler);
                    resultSheets.add(new ExcelSheet("sheet" + (i + 1), sheetHandler.getRows(), sheetHandler.getMergedRegions()));
                }
            }

            return new ExcelWorkbook(resultSheets);
        } catch (Exception e) {
            throw new IOException("Failed to parse xlsx: " + e.getMessage(), e);
        }
    }

    private static byte[] readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = zis.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    public ExcelSheet getSheetAt(int index) {
        if (index >= 0 && index < sheets.size()) {
            return sheets.get(index);
        }
        return null;
    }

    public int getNumberOfSheets() {
        return sheets.size();
    }

    @Override
    public void close() {
        // In-memory model, nothing to close
    }

    // sax handler for shared strings table (xl/sharedStrings.xml)
    private static class SharedStringsHandler extends DefaultHandler {
        private final List<String> strings = new ArrayList<>();
        private StringBuilder currentSi = null;
        private boolean inT = false;

        public List<String> getStrings() {
            return strings;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("si".equals(qName)) {
                currentSi = new StringBuilder();
            } else if ("t".equals(qName) && currentSi != null) {
                inT = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inT && currentSi != null) {
                currentSi.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("t".equals(qName)) {
                inT = false;
            } else if ("si".equals(qName)) {
                strings.add(currentSi != null ? currentSi.toString() : "");
                currentSi = null;
            }
        }
    }

    // sax handler for worksheet xml (rows, cells, and merged regions)
    private static class SheetHandler extends DefaultHandler {
        private final List<String> sst;
        private final Map<Integer, Map<Integer, String>> rows = new HashMap<>();
        private final List<ExcelRange> mergedRegions = new ArrayList<>();

        private int currentRow = -1;
        private int currentCol = -1;
        private String cellType = null;
        private StringBuilder currentVal = null;
        private boolean inV = false;
        private boolean inT = false;

        public SheetHandler(List<String> sst) {
            this.sst = sst;
        }

        public Map<Integer, Map<Integer, String>> getRows() {
            return rows;
        }

        public List<ExcelRange> getMergedRegions() {
            return mergedRegions;
        }

        private static int colNameToIndex(String col) {
            int result = 0;
            for (int i = 0; i < col.length(); i++) {
                result = result * 26 + (Character.toUpperCase(col.charAt(i)) - 'A' + 1);
            }
            return result - 1;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("row".equals(qName)) {
                String rAttr = attributes.getValue("r");
                if (rAttr != null) {
                    currentRow = Integer.parseInt(rAttr) - 1;
                } else {
                    currentRow++;
                }
            } else if ("c".equals(qName)) {
                String rRef = attributes.getValue("r");
                if (rRef != null) {
                    Matcher m = CELL_REF_PATTERN.matcher(rRef);
                    if (m.matches()) {
                        currentCol = colNameToIndex(m.group(1));
                        currentRow = Integer.parseInt(m.group(2)) - 1;
                    } else {
                        currentCol++;
                    }
                } else {
                    currentCol++;
                }
                cellType = attributes.getValue("t");
                currentVal = new StringBuilder();
            } else if ("v".equals(qName)) {
                inV = true;
            } else if ("t".equals(qName)) {
                inT = true;
            } else if ("mergeCell".equals(qName)) {
                String ref = attributes.getValue("ref");
                if (ref != null) {
                    ExcelRange r = ExcelRange.parse(ref);
                    if (r != null) {
                        mergedRegions.add(r);
                    }
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if ((inV || inT) && currentVal != null) {
                currentVal.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("v".equals(qName)) {
                inV = false;
            } else if ("t".equals(qName)) {
                inT = false;
            } else if ("c".equals(qName)) {
                String text = "";
                if (currentVal != null) {
                    String raw = currentVal.toString().trim();
                    if ("s".equals(cellType)) {
                        try {
                            int idx = Integer.parseInt(raw);
                            if (idx >= 0 && idx < sst.size()) {
                                text = sst.get(idx);
                            }
                        } catch (Exception ignored) {}
                    } else if ("b".equals(cellType)) {
                        text = "1".equals(raw) ? "TRUE" : "FALSE";
                    } else {
                        // Handles "inlineStr", "str" (formula string), and direct values/numbers
                        text = raw;
                    }
                }
                if (currentRow >= 0 && currentCol >= 0) {
                    rows.computeIfAbsent(currentRow, k -> new HashMap<>()).put(currentCol, text);
                }
                cellType = null;
                currentVal = null;
            }
        }
    }
}
