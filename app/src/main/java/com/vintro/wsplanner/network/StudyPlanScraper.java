package com.vintro.wsplanner.network;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.enums.DegreeLevel;

public class StudyPlanScraper {

    public static Integer getEnglishGroup(OkHttpClient client) {
        Request request = new Request.Builder().url(PUW.homeUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            Document doc = Jsoup.parse(response.body().string());

            // Ищем все ссылки с атрибутом title, как делали это для направлений
            Elements courseLinks = doc.select("a[title]");

            for (Element link : courseLinks) {
                String title = link.attr("title");

                // Проверяем, что это курс английского
                if (title.toLowerCase().contains("język angielski") && title.toLowerCase().contains("lektorat")) {

                    // Регулярное выражение ищет слово "grupa", один или несколько пробелов,
                    // и захватывает идущие за ними цифры в группу 1
                    Pattern pattern = Pattern.compile("grupa\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(title);

                    if (matcher.find()) {
                        String groupNumberStr = matcher.group(1);
                        Integer groupNumber = Integer.parseInt(groupNumberStr);
                        Logger.d("StudyPlanScraper", "English group extracted: " + groupNumber);
                        return groupNumber;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper", "Error fetching English group: " + e.getMessage());
        }

        return null; // Если группа не найдена или студент не изучает английский
    }

    // Шаг 1: Получаем все доступные направления с главной страницы
    public static List<ParsedCourse> getAvailableCourses(OkHttpClient client) {
        List<ParsedCourse> courses = new ArrayList<>();

        Request request = new Request.Builder().url(PUW.homeUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return courses;

            Document doc = Jsoup.parse(response.body().string());
            // Ищем ссылки, в названии которых есть "Strefa studenta" (на основе image_87d022.jpg)
            Elements courseLinks = doc.select("a[title*='Strefa studenta']");

            for (Element link : courseLinks) {
                ParsedCourse course = new ParsedCourse();
                course.courseUrl = link.attr("href");

                String title = link.attr("title"); // Пример: "Strefa studenta - kierunek Administracja - studia I stopnia"
                String[] parts = title.split(" - ");

                if (parts.length >= 2) {
                    course.fieldOfStudy = parts[1].replace("kierunek", "").trim();
                }

                if (parts.length >= 3) {
                    course.degreeLevel = parseDegreeLevel(parts[2]);
                }

                // Шаг 2 и 3: Подтягиваем детальную информацию изнутри курса
                fetchCourseDetails(client, course);
                courses.add(course);
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper", "Error parsing courses: " + e.getMessage());
        }

        return courses;
    }

    // Шаг 2: Проверяем папки (включая те, в названии которых нет режима обучения)
    private static void fetchCourseDetails(OkHttpClient client, ParsedCourse course) {
        Request request = new Request.Builder().url(course.courseUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string());
            Elements folderLinks = doc.select("div.activityinstance > a");

            for (Element folderLink : folderLinks) {
                String folderName = folderLink.select("span.instancename").text().toLowerCase();
                if (!folderName.contains("plany zajęć")) continue;

                // Пытаемся получить режим из названия папки (может вернуть null, если папка называется просто "Plany zajęć")
                String folderMode = determineStudyMode(folderName);
                String folderUrl = folderLink.attr("href");

                // Шаг 3: Передаем парсинг файлов в метод, который самостоятельно заполнит структуру данных
                processFolderFiles(client, folderUrl, course, folderMode);
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper", "Error fetching course details: " + e.getMessage());
        }
    }

    // Шаг 3: Парсим файлы .xlsx, достаем года учебы и режим (если он не был определен по папке)
    private static void processFolderFiles(OkHttpClient client, String folderUrl, ParsedCourse course, String folderMode) {
        Request request = new Request.Builder().url(folderUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string());
            Elements files = doc.select("span.fp-filename");

            for (Element file : files) {
                String fileName = file.text();
                if (!fileName.endsWith(".xlsx")) continue;

                // Извлекаем режим обучения и римскую цифру года с помощью регулярного выражения.
                // Приоритет в группе OR важен: сначала ищем "nst puw", затем "nst", затем "st".
                Pattern pattern = Pattern.compile("(nst puw|nst|st)\\s+([IV]+)\\s*-", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(fileName);

                if (matcher.find()) {
                    String fileMode = matcher.group(1).toLowerCase();
                    String romanYear = matcher.group(2);
                    int year = romanToInt(romanYear);

                    // Если папка не содержала режим обучения, берем его непосредственно из названия файла
                    String effectiveMode = (folderMode != null) ? folderMode : fileMode;

                    // Добавляем найденный год к соответствующему режиму обучения
                    course.availableModesAndYears.putIfAbsent(effectiveMode, new ArrayList<>());
                    if (!course.availableModesAndYears.get(effectiveMode).contains(year)) {
                        course.availableModesAndYears.get(effectiveMode).add(year);
                    }
                }

                // Fallback: Если уровень обучения не был найден из заголовка курса, берем его из названия файла
                if (course.degreeLevel == null) {
                    String lowerFileName = fileName.toLowerCase();
                    if (lowerFileName.contains("i stopnia")) course.degreeLevel = DegreeLevel.BACHELORS;
                    else if (lowerFileName.contains("ii stopnia")) course.degreeLevel = DegreeLevel.MASTERS;
                    else if (lowerFileName.contains("jednolite")) course.degreeLevel = DegreeLevel.LONG_MASTERS;
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper", "Error processing folder files: " + e.getMessage());
        }
    }

    // Вспомогательные методы

    private static DegreeLevel parseDegreeLevel(String text) {
        text = text.toLowerCase();
        if (text.contains("ii stopnia")) return DegreeLevel.MASTERS;
        if (text.contains("i stopnia")) return DegreeLevel.BACHELORS;

        if (text.contains("jednolite")) return DegreeLevel.LONG_MASTERS;
        return null;
    }

    private static String determineStudyMode(String folderName) {
        if (folderName.contains("puw") || folderName.contains("odległość")) return "nst puw";
        if (folderName.contains("niestacjonarne")) return "nst";
        if (folderName.contains("stacjonarne")) return "st";
        return null;
    }

    private static int romanToInt(String roman) {
        switch (roman.toUpperCase()) {
            case "I": return 1;
            case "II": return 2;
            case "III": return 3;
            case "IV": return 4;
            case "V": return 5;
            default: return 1; // Fallback
        }
    }
}