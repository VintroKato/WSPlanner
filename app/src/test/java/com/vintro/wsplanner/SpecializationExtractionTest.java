package com.vintro.wsplanner;

import com.vintro.wsplanner.parser.ParserUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

// unit tests for specialization extraction from excel sheets
public class SpecializationExtractionTest {

    // generate minimal in-memory xlsx file with sample column headers
    private byte[] createTestExcel(String cellB3, String cellC3) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // sharedStrings
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            String sst = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"2\" uniqueCount=\"2\">\n" +
                    "    <si><t>" + cellB3 + "</t></si>\n" +
                    "    <si><t>" + cellC3 + "</t></si>\n" +
                    "</sst>";
            zos.write(sst.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // sheet1.xml
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                    "    <sheetData>\n" +
                    "        <row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>\n" +
                    "        <row r=\"2\"><c r=\"A2\" t=\"s\"><v>0</v></c></row>\n" +
                    "        <row r=\"3\"><c r=\"A3\" t=\"s\"><v>0</v></c></row>\n" +
                    "        <row r=\"4\">\n" +
                    "            <c r=\"A4\"><v>0</v></c>\n" +
                    "            <c r=\"B4\" t=\"s\"><v>0</v></c>\n" +
                    "            <c r=\"C4\" t=\"s\"><v>1</v></c>\n" +
                    "        </row>\n" +
                    "    </sheetData>\n" +
                    "</worksheet>";
            zos.write(sheet.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    public void testExtractionFromExcelCells() throws Exception {
        byte[] bytes1 = createTestExcel("Sp: Sztuczna inteligencja gr.1", "Sp: Sztuczna inteligencja gr.2");
        List<String> specs1 = ParserUtils.extractSpecializations(new ByteArrayInputStream(bytes1));
        assertEquals(1, specs1.size());
        assertEquals("Sztuczna inteligencja", specs1.get(0));

        byte[] bytes2 = createTestExcel("Sp.: Sztuczna inteligencja", "Sp.: Sztuczna inteligencja gr.2");
        List<String> specs2 = ParserUtils.extractSpecializations(new ByteArrayInputStream(bytes2));
        assertEquals(1, specs2.size());
        assertEquals("Sztuczna inteligencja", specs2.get(0));

        byte[] bytes3 = createTestExcel("Sp.: Cyberbezpieczeństwo i informatyka śledcza - gr.1", "Sp.: Cyberbezpieczeństwo i informatyka śledcza - gr.2");
        List<String> specs3 = ParserUtils.extractSpecializations(new ByteArrayInputStream(bytes3));
        assertEquals(1, specs3.size());
        assertEquals("Cyberbezpieczeństwo i informatyka śledcza", specs3.get(0));

        // Test filtering of exam blocks with embedded Sp:
        String examCell = "EGZAMINY:\n" +
                "Rachunek prawdopodobieństwa i statystyka, Podstawy teorii grafów, Programowanie, Wstęp do inżynierii oprogramowania\n" +
                "Sp.: Technologie Webowe i Internet rzeczy: Programowanie baz danych\n" +
                "Sp.: Sztuczna inteligencja: Statystyka matematyczna\n" +
                "Sp.: Grafika komputerowa i projektowanie gier: Grafika komputerowa cz. 1";
        assertNull(ParserUtils.cleanSpecializationName(examCell));

        // Test multiline column headers as found in Informatyka semester III
        assertEquals("Technologie Webowe i Internet rzeczy",
                ParserUtils.cleanSpecializationName("Sp.: Technologie Webowe \n i Internet rzeczy"));
        assertEquals("Sztuczna inteligencja",
                ParserUtils.cleanSpecializationName("Sp.: Sztuczna \n inteligencja\ngr.1"));
        assertEquals("Sztuczna inteligencja",
                ParserUtils.cleanSpecializationName("Sp.: Sztuczna \n inteligencja\ngr.2"));
        assertEquals("Grafika komputerowa i projektowanie gier",
                ParserUtils.cleanSpecializationName("Sp.: Grafika \n komputerowa i \n projektowanie gier"));
    }

    @Test
    public void testCleanSubjectName() {
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp.: Programowanie w języku JAVA - 20h"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp: Programowanie w języku JAVA"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp. : Programowanie w języku JAVA"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp.:Programowanie w języku JAVA"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp:Programowanie w języku JAVA"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Sp. Programowanie w języku JAVA"));
        assertEquals("Programowanie w języku JAVA",
                ParserUtils.cleanSubjectName("Specjalność: Programowanie w języku JAVA"));
        assertEquals("Projektowanie graficznych interfejsów użytkownika",
                ParserUtils.cleanSubjectName("Sp.: Projektowanie graficznych interfejsów użytkownika - 20h"));
        assertEquals("Sieci neuronowe cz. 1",
                ParserUtils.cleanSubjectName("Sp.: Sieci neuronowe cz. 1 - 30h"));
        assertEquals("Wstęp do inżynierii oprogramowania",
                ParserUtils.cleanSubjectName("Wstęp do inżynierii oprogramowania - 20h"));
    }
}
