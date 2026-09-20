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
import java.util.stream.Collectors;
import com.vintro.wsplanner.utils.Logger;

public class ParserUtils {
    // get unique specializations from header
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

                // explicit specialization (sp.: ...)
                Matcher m = Pattern.compile("(?i)(?:sp\\.|specjalno[śs][ćc])\\s*:?\\s*([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ\\s]+)").matcher(fullText);
                if (m.find()) {
                    String spec = m.group(1).trim();
                    spec = spec.replaceAll("(?i)grupa.*|podzia[lł].*", "").trim();
                    specializations.add(spec);
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
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("ParserUtils.extractSpecializations", e.getMessage());
            e.printStackTrace();
        }
        Logger.d("ParserUtils.extractSpecializations", specializations.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return new ArrayList<>(specializations);
    }
}
