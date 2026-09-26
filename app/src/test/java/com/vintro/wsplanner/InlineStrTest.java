package com.vintro.wsplanner;

import com.vintro.wsplanner.parser.excel.ExcelCell;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.parser.excel.ExcelWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class InlineStrTest {

    @Test
    public void testInlineStrAndFormulaStrParsing() throws Exception {
        // Construct a synthetic .xlsx in-memory with inlineStr, formula str, boolean, and regular numbers
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // sheet1.xml
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            String sheetXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                    "    <sheetData>\n" +
                    "        <row r=\"1\">\n" +
                    "            <!-- A1: inlineStr with simple <t> -->\n" +
                    "            <c r=\"A1\" t=\"inlineStr\"><is><t>Programowanie</t></is></c>\n" +
                    "            <!-- B1: inlineStr with rich text (<r><t>) -->\n" +
                    "            <c r=\"B1\" t=\"inlineStr\"><is><r><t>Projekt </t></r><r><t>zespołowy</t></r></is></c>\n" +
                    "            <!-- C1: boolean -->\n" +
                    "            <c r=\"C1\" t=\"b\"><v>1</v></c>\n" +
                    "            <!-- D1: formula string (t=\"str\") -->\n" +
                    "            <c r=\"D1\" t=\"str\"><f>CONCATENATE(A1, \" \", B1)</f><v>Wykład online</v></c>\n" +
                    "        </row>\n" +
                    "    </sheetData>\n" +
                    "    <mergeCells count=\"1\">\n" +
                    "        <mergeCell ref=\"A1:B1\"/>\n" +
                    "    </mergeCells>\n" +
                    "</worksheet>";
            zos.write(sheetXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        try (ExcelWorkbook wb = ExcelWorkbook.create(new ByteArrayInputStream(baos.toByteArray()))) {
            assertNotNull(wb);
            assertEquals(1, wb.getNumberOfSheets());
            ExcelSheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);

            // A1
            ExcelCell a1 = sheet.getRow(0).getCell(0);
            assertNotNull(a1);
            assertEquals("Programowanie", a1.asString());

            // B1 (rich text parts combined)
            ExcelCell b1 = sheet.getRow(0).getCell(1);
            assertNotNull(b1);
            assertEquals("Projekt zespołowy", b1.asString());

            // C1 (boolean)
            ExcelCell c1 = sheet.getRow(0).getCell(2);
            assertNotNull(c1);
            assertEquals("TRUE", c1.asString());

            // D1 (str from formula)
            ExcelCell d1 = sheet.getRow(0).getCell(3);
            assertNotNull(d1);
            assertEquals("Wykład online", d1.asString());

            // Merged region check
            assertEquals(1, sheet.getMergedRegions().size());
            assertEquals("A1:B1", sheet.getMergedRegions().get(0).formatAsString());
        }
    }
}
