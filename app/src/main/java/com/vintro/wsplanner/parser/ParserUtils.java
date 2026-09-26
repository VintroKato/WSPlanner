package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.parser.excel.ExcelCell;
import com.vintro.wsplanner.parser.excel.ExcelRow;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.parser.excel.ExcelWorkbook;
import com.vintro.wsplanner.utils.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// utility helpers for schedule parsing and text cleaning
public class ParserUtils {

    // clean subject name by removing specialization prefixes and trailing hour counts
    public static String cleanSubjectName(String rawSubject) {
        if (rawSubject == null) return null;
        String subject = rawSubject.trim();
        if (subject.isEmpty()) return subject;

        // remove prefix like Sp.:, Sp:, Specjalność: etc.
        subject = subject.replaceAll("(?i)^(?:sp\\b\\.?\\s*:?\\s*|specjalno[śs][ćc]\\s*:?\\s*)", "").trim();

        // clean trailing duration/hours (e.g. - 15h, 20h)
        subject = subject.replaceAll("(?i)\\s*[-–—]?\\s*\\d{1,2}h\\b", "").trim();

        // clean leading/trailing punctuation and whitespace
        subject = subject.replaceAll("^[\\s:–—\\-]+|[\\s:–—.,\\-]+$", "").trim();

        return subject;
    }

    // clean and normalize specialization name removing Sp: prefixes and group suffixes
    public static String cleanSpecializationName(String rawText) {
        if (rawText == null) return null;
        String text = rawText.trim();
        if (text.isEmpty()) return null;

        String lowerFull = text.toLowerCase();
        // Ignore exam blocks, schedule metadata and informational notes
        if (lowerFull.contains("egzamin") ||
                lowerFull.contains("harmonogram") ||
                lowerFull.contains("program studi") ||
                lowerFull.contains("zjazdy") ||
                lowerFull.contains("studia zawieraj") ||
                lowerFull.contains("uwagi")) {
            return null;
        }

        // Join multiline header text into a single line (Excel headers often wrap across lines)
        String singleLine = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();

        // Check if there is an explicit specialization marker (longer prefixes first to avoid "sp" matching inside "specjalność")
        Pattern specPrefix = Pattern.compile("(?i)^(?:specjalno[śs][ćc]|spec\\b\\.?|sp\\b\\.?)\\s*:?\\s*(.*)", Pattern.DOTALL);
        Matcher m = specPrefix.matcher(singleLine);
        String spec;
        if (m.find()) {
            spec = m.group(1).trim();
        } else {
            // If the cell doesn't start with Sp:/Specjalność:, check if singleLine has inferred spec
            String lower = singleLine.toLowerCase();
            if (lower.startsWith("gr") || lower.startsWith("grupa") || lower.startsWith("rok") ||
                    lower.startsWith("godz") || lower.startsWith("sala") || lower.startsWith("dni") ||
                    lower.startsWith("poniedzia") || lower.startsWith("wtorek") || lower.startsWith("środa") ||
                    lower.startsWith("czwartek") || lower.startsWith("piątek") || lower.startsWith("sobota") ||
                    lower.startsWith("niedziela") || lower.contains("semestr") || lower.contains("rok akademicki") ||
                    singleLine.length() <= 8) {
                return null;
            }
            spec = singleLine;
        }

        // If there is another colon inside (e.g. "Technologie Webowe: Programowanie baz danych"),
        // the part after the colon is the course/exam title, not the specialization name
        if (spec.contains(":")) {
            spec = spec.substring(0, spec.indexOf(":")).trim();
        }

        // Clean group, subgroup, surname markers from the end of the specialization name
        // e.g. "Sztuczna inteligencja gr.1", "Sztuczna inteligencja - gr. 2", "Sztuczna inteligencja (gr. 1)", "Sztuczna inteligencja gr"
        spec = spec.replaceAll("(?i)\\s*[-–—/(]?\\s*\\b(?:gr(?:upa)?\\.?\\s*\\d*|podzia[lł].*|wg\\s+nazwisk.*|[a-ząćęłńóśźż]+-[a-ząćęłńóśźż]+)\\b.*", "");
        // Remove trailing "gr" or "gr." if still hanging
        spec = spec.replaceAll("(?i)\\s+gr\\.?$", "");
        // Clean leading/trailing punctuation and whitespace
        spec = spec.replaceAll("^[\\s:–—\\-]+|[\\s:–—.,\\-]+$", "").trim();

        return spec.isEmpty() ? null : spec;
    }

    // get unique specializations from header
    public static List<String> extractSpecializations(InputStream excelStream) {
        Logger.d("ParserUtils.extractSpecializations", "Starting extraction of specializations from Excel stream");
        Set<String> specializations = new LinkedHashSet<>();

        try (ExcelWorkbook workbook = ExcelWorkbook.create(excelStream)) {
            ExcelSheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                Logger.w("ParserUtils.extractSpecializations", "Sheet 0 is null");
                return new ArrayList<>();
            }

            // Scan header rows 1 to 4 where groups and specializations are defined
            for (int r = 1; r <= 4; r++) {
                ExcelRow row = sheet.getRow(r);
                if (row == null) continue;

                for (ExcelCell cell : row) {
                    String fullText = (cell != null ? cell.asString() : "").trim();
                    if (fullText.isEmpty()) continue;

                    String spec = cleanSpecializationName(fullText);
                    if (spec != null && !spec.isEmpty()) {
                        specializations.add(spec);
                        Logger.d("ParserUtils.extractSpecializations", "Found specialization: '" + spec + "' from cell text: '" + fullText + "'");
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("ParserUtils.extractSpecializations", "Error extracting specializations: " + e.getMessage());
            e.printStackTrace();
        }
        Logger.i("ParserUtils.extractSpecializations", "Extracted specializations count: " + specializations.size() + " -> " + specializations.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return new ArrayList<>(specializations);
    }
}
