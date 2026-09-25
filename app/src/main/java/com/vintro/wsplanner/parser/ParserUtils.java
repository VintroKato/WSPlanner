package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.parser.excel.ExcelCell;
import com.vintro.wsplanner.parser.excel.ExcelRow;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.parser.excel.ExcelWorkbook;
import com.vintro.wsplanner.utils.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ParserUtils {
    // get unique specializations from header
    public static List<String> extractSpecializations(InputStream excelStream) {
        Logger.d("ParserUtils.extractSpecializations", "Starting extraction of specializations from Excel stream");
        Set<String> specializations = new HashSet<>();

        try (ExcelWorkbook workbook = ExcelWorkbook.create(excelStream)) {
            ExcelSheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                Logger.w("ParserUtils.extractSpecializations", "Sheet 0 is null");
                return new ArrayList<>();
            }
            ExcelRow groupRow = sheet.getRow(3);

            if (groupRow == null) {
                Logger.w("ParserUtils.extractSpecializations", "Row 3 (group row) is null in sheet");
                return new ArrayList<>();
            }

            for (ExcelCell cell : groupRow) {
                String fullText = (cell != null ? cell.asString() : "").trim();
                if (fullText.isEmpty()) continue;

                String lowerText = fullText.toLowerCase();

                // explicit specialization (sp.: ...)
                Matcher m = Pattern.compile("(?i)(?:sp\\.|specjalno[śs][ćc])\\s*:?\\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ\\s]+)").matcher(fullText);
                if (m.find()) {
                    String spec = m.group(1).trim();
                    spec = spec.replaceAll("(?i)grupa.*|podzia[lł].*", "").trim();
                    specializations.add(spec);
                    Logger.d("ParserUtils.extractSpecializations", "Found explicit specialization: '" + spec + "' from cell text: '" + fullText + "'");
                    continue;
                }

                // non-group column with long first line
                if (!lowerText.startsWith("grupa ")) {
                    String[] lines = fullText.split("\n");
                    String firstLine = lines[0].trim();

                    // skip group labels and check length
                    if (!firstLine.toLowerCase().startsWith("gr") && firstLine.length() > 8) {
                        String specFallback = firstLine.replaceAll("(?i)grupa.*|podzia[lł].*|nazwisk.*", "").trim();
                        if (!specFallback.isEmpty()) {
                            specializations.add(specFallback);
                            Logger.d("ParserUtils.extractSpecializations", "Found inferred specialization: '" + specFallback + "' from cell text: '" + fullText + "'");
                        }
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

