package com.vintro.wsplanner.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserUtils {
    /**
     * Сканирует шапку и возвращает список уникальных специализаций для выбора в UI.
     */
    public static List<String> extractSpecializations(InputStream excelStream) {
        Set<String> specializations = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(excelStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row groupRow = sheet.getRow(3);

            if (groupRow == null) return new ArrayList<>();

            for (Cell cell : groupRow) {
                String fullText = formatter.formatCellValue(cell).trim();
                if (fullText.isEmpty()) continue;

                String lowerText = fullText.toLowerCase();

                // 1. Явная специализация ("Sp.: ...")
                Matcher m = Pattern.compile("(?i)(?:sp\\.|specjalno[śs][ćc])\\s*:?\\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ\\s]+)").matcher(fullText);
                if (m.find()) {
                    String spec = m.group(1).trim();
                    spec = spec.replaceAll("(?i)grupa.*|podzia[lł].*", "").trim();
                    specializations.add(spec);
                    continue;
                }

                // 2. Если это не явная "GRUPA 1", и первая строка длинная - это скрытая специализация
                if (!lowerText.startsWith("grupa ")) {
                    String[] lines = fullText.split("\n");
                    String firstLine = lines[0].trim();

                    // Если первая строчка не похожа на "gr. 2" и достаточно длинная
                    if (!firstLine.toLowerCase().startsWith("gr") && firstLine.length() > 8) {
                        String specFallback = firstLine.replaceAll("(?i)grupa.*|podzia[lł].*|nazwisk.*", "").trim();
                        if (!specFallback.isEmpty()) {
                            specializations.add(specFallback);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>(specializations);
    }
}
