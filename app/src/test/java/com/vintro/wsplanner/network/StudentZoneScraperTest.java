package com.vintro.wsplanner.network;

import com.vintro.wsplanner.enums.DegreeLevel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StudentZoneScraperTest {

    @Test
    public void testStudentZonePriorityMatching() {
        String html = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=101\">Strefa Studenta - Ogólna</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=202\">Strefa studenta - kierunek Zarządzanie - studia I stopnia</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=303\">Strefa studenta - kierunek Informatyka - studia I stopnia</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=404\">Strefa studenta - kierunek Informatyka - studia II stopnia</a>" +
                "</body></html>";

        // student studying computer science bachelors
        String urlInformatyka1 = StudyPlanScraper.extractStudentZoneUrlFromHtml(html, "Informatyka", DegreeLevel.BACHELORS);
        assertNotNull(urlInformatyka1);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=303", urlInformatyka1);

        // student studying computer science masters
        String urlInformatyka2 = StudyPlanScraper.extractStudentZoneUrlFromHtml(html, "Informatyka", DegreeLevel.MASTERS);
        assertNotNull(urlInformatyka2);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=404", urlInformatyka2);

        // student studying management bachelors
        String urlZarzadzanie = StudyPlanScraper.extractStudentZoneUrlFromHtml(html, "Zarządzanie", DegreeLevel.BACHELORS);
        assertNotNull(urlZarzadzanie);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=202", urlZarzadzanie);

        // student with major matching general course title
        String htmlNoDegree = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=101\">Strefa Studenta</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=505\">Strefa studenta - Finanse</a>" +
                "</body></html>";
        String urlFinanse = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlNoDegree, "Finanse i rachunkowość", DegreeLevel.BACHELORS);
        assertNotNull(urlFinanse);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=505", urlFinanse);

        // unknown major falls back to generic student zone
        String urlGeneric = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlNoDegree, "Architektura", DegreeLevel.BACHELORS);
        assertNotNull(urlGeneric);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=101", urlGeneric);
    }

    @Test
    public void testRokZerowyPriorityAndMatching() {
        String htmlMixed = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=808\">Strefa uczestnika kursu \"ROK ZEROWY\"</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=303\">Strefa studenta - kierunek Informatyka - studia I stopnia</a>" +
                "</body></html>";

        // degree student zone takes priority over finished rok zerowy zone
        String urlRegular = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlMixed, "Informatyka", DegreeLevel.BACHELORS);
        assertNotNull(urlRegular);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=303", urlRegular);

        // student studying rok zerowy resolves participant zone
        String urlZeroYear = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlMixed, "Rok zerowy", DegreeLevel.ZERO_YEAR);
        assertNotNull(urlZeroYear);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=808", urlZeroYear);

        // student with only rok zerowy participant zone
        String htmlOnlyZero = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=808\">Strefa uczestnika kursu \"ROK ZEROWY\"</a>" +
                "</body></html>";
        String urlOnly = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlOnlyZero, "Rok zerowy", DegreeLevel.ZERO_YEAR);
        assertNotNull(urlOnly);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=808", urlOnly);
    }
}
