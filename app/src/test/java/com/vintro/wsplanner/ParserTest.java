package com.vintro.wsplanner;

import org.junit.Test;
import java.io.InputStream;
import com.vintro.wsplanner.parser.BachelorFullTimeParser;
import com.vintro.wsplanner.models.Schedule;
import static org.junit.Assert.*;

public class ParserTest {

    @Test
    public void testExcelParser() throws Exception {
        // Загружаем файл из папки test/resources
        InputStream is = getClass().getClassLoader().getResourceAsStream("hard_Informatyka - studia I stopnia - st III - semestr zimowy (11).xlsx");
        assertNotNull("File not found", is);

        BachelorFullTimeParser parser = new BachelorFullTimeParser();

        // Передаем параметры: поток, направление, семестр, основная группа, языковая группа
        Schedule schedule = parser.parse(is, "Informatyka", 6, "Lar", "webowe", "2");

        // Выводим результат в консоль
        System.out.println("====== RESULT ======");
        System.out.println(schedule.toString());
        System.out.println("Found lessond: " + schedule.getLessons().size());

        // Можно добавить проверки, чтобы тест падал, если парсер ничего не нашел
        assertTrue(schedule.getLessons().size() > 0);
    }
}