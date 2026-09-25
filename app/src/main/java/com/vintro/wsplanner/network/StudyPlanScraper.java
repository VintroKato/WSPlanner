package com.vintro.wsplanner.network;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;

public class StudyPlanScraper {

    public static String extractSeminarTeacherFromCourseTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return null;
        }
        String title = rawTitle.trim();
        String lower = title.toLowerCase();

        // must be seminar or diploma
        if (!lower.contains("seminarium") && !lower.contains("dyplom")) {
            return null;
        }

        // skip registration or enrollment
        if (lower.contains("zapisy") || lower.contains("nabór") || lower.contains("nabor") || lower.contains("rekrutacja")) {
            return null;
        }

        // split by dash delimiters
        String[] parts = title.split("\\s*[-–—]\\s*");
        if (parts.length < 2) {
            return null;
        }

        // search backwards for teacher name
        for (int i = parts.length - 1; i >= 1; i--) {
            String part = parts[i].trim();
            String partLower = part.toLowerCase();

            // skip years or semester labels
            if (partLower.contains("sem.") || partLower.contains("semestr") ||
                partLower.contains("nabór") || partLower.contains("nabor") ||
                partLower.contains("rok") || partLower.matches(".*\\b20\\d{2}\\b.*") ||
                partLower.matches(".*\\b\\d{2}/\\d{2}\\b.*") ||
                partLower.matches(".*\\b\\d{4}/\\d{4}\\b.*")) {
                continue;
            }

            // check for academic title
            boolean hasAcademicTitle = partLower.contains("mgr") || partLower.contains("dr") ||
                                       partLower.contains("prof") || partLower.contains("doc") ||
                                       partLower.contains("inż") || partLower.contains("inz");

            // check for name and surname pattern
            boolean looksLikeName = part.matches("^[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+(\\s+[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+)+$");

            if (hasAcademicTitle || looksLikeName) {
                return part;
            }
        }

        return null;
    }

    public static String getSeminarTeacher(OkHttpClient client) {
        String teacher = findSeminarTeacherInUrl(client, PUW.homeUrl);
        if (teacher != null) return teacher;

        // check courses page as fallback
        teacher = findSeminarTeacherInUrl(client, PUW.baseUrl + "my/courses.php");
        return teacher;
    }

    private static String findSeminarTeacherInUrl(OkHttpClient client, String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            Document doc = Jsoup.parse(response.body().string());

            // check title attributes
            Elements titleElements = doc.select("[title]");
            for (Element el : titleElements) {
                String title = el.attr("title");
                String teacher = extractSeminarTeacherFromCourseTitle(title);
                if (teacher != null) {
                    Logger.d("StudyPlanScraper.findSeminarTeacherInUrl", "Seminar teacher extracted from title attribute: " + teacher);
                    return teacher;
                }
            }

            // check course links and names
            Elements courseLinks = doc.select("a[href*='course/view.php'], .coursename, .course-info-container, span.multiline, h3, h4");
            for (Element el : courseLinks) {
                String text = el.text();
                String teacher = extractSeminarTeacherFromCourseTitle(text);
                if (teacher != null) {
                    Logger.d("StudyPlanScraper.findSeminarTeacherInUrl", "Seminar teacher extracted from element text: " + teacher);
                    return teacher;
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.findSeminarTeacherInUrl", "Error fetching seminar teacher from " + url + ": " + e.getMessage());
        }
        return null;
    }

    public static Integer getEnglishGroup(OkHttpClient client) {
        Request request = new Request.Builder().url(PUW.homeUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            Document doc = Jsoup.parse(response.body().string());

            // find links with title attribute
            Elements courseLinks = doc.select("a[title]");

            for (Element link : courseLinks) {
                String title = link.attr("title");

                // check if english course
                if (title.toLowerCase().contains("język angielski") && title.toLowerCase().contains("lektorat")) {

                    // extract group number after grupa keyword
                    Pattern pattern = Pattern.compile("grupa\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(title);

                    if (matcher.find()) {
                        String groupNumberStr = matcher.group(1);
                        Integer groupNumber = Integer.parseInt(groupNumberStr);
                        Logger.d("StudyPlanScraper.getEnglishGroup", "English group extracted: " + groupNumber);
                        return groupNumber;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.getEnglishGroup", "Error fetching English group: " + e.getMessage());
        }

        return null; // not found
    }

    // get available courses from home page
    public static List<ParsedCourse> getAvailableCourses(OkHttpClient client) {
        Logger.d("StudyPlanScraper.getAvailableCourses", "Fetching available courses from PUW home page");
        List<ParsedCourse> courses = new ArrayList<>();

        Request request = new Request.Builder().url(PUW.homeUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Logger.e("StudyPlanScraper.getAvailableCourses", "Home page request failed with code: " + response.code());
                return courses;
            }

            Document doc = Jsoup.parse(response.body().string());
            // find student zone links
            Elements courseLinks = doc.select("a[title*='Strefa studenta']");

            for (Element link : courseLinks) {
                ParsedCourse course = new ParsedCourse();
                course.courseUrl = link.attr("href");

                String title = link.attr("title");
                String[] parts = title.split(" - ");

                if (parts.length >= 2) {
                    course.fieldOfStudy = parts[1].replace("kierunek", "").trim();
                }

                if (parts.length >= 3) {
                    course.degreeLevel = parseDegreeLevel(parts[2]);
                }

                // load course details
                fetchCourseDetails(client, course);
                courses.add(course);
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.getAvailableCourses", "Error parsing courses: " + e.getMessage());
        }
        Logger.i("StudyPlanScraper.getAvailableCourses", "Parsed " + courses.size() + " available course(s): " + courses.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return courses;
    }

    // check course folders
    private static void fetchCourseDetails(OkHttpClient client, ParsedCourse course) {
        Request request = new Request.Builder().url(course.courseUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string());
            Elements folderLinks = doc.select("div.activityinstance > a");

            for (Element folderLink : folderLinks) {
                String folderName = folderLink.select("span.instancename").text().toLowerCase();
                if (!folderName.contains("plany zajęć")) continue;

                // try getting study mode from folder name
                String folderMode = determineStudyMode(folderName);
                String folderUrl = folderLink.attr("href");

                // parse schedule files
                processFolderFiles(client, folderUrl, course, folderMode);
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.fetchCourseDetails", "Error fetching course details: " + e.getMessage());
        }
    }

    // parse xlsx files for study year and mode
    private static void processFolderFiles(OkHttpClient client, String folderUrl, ParsedCourse course, String folderMode) {
        Request request = new Request.Builder().url(folderUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string());
            Elements files = doc.select("span.fp-filename");

            for (Element file : files) {
                String fileName = file.text();
                if (!fileName.endsWith(".xlsx")) continue;

                // extract mode and roman numeral year
                Pattern pattern = Pattern.compile("(nst puw|nst|st)\\s+([IV]+)\\s*-", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(fileName);

                if (matcher.find()) {
                    String fileMode = matcher.group(1).toLowerCase();
                    String romanYear = matcher.group(2);
                    int year = romanToInt(romanYear);

                    // fallback to mode from file name
                    String effectiveMode = (folderMode != null) ? folderMode : fileMode;

                    // add year to mode
                    course.availableModesAndYears.putIfAbsent(effectiveMode, new ArrayList<>());
                    if (!course.availableModesAndYears.get(effectiveMode).contains(year)) {
                        course.availableModesAndYears.get(effectiveMode).add(year);
                    }
                }

                // fallback to degree level from file name
                if (course.degreeLevel == null) {
                    String lowerFileName = fileName.toLowerCase();
                    if (lowerFileName.contains("i stopnia")) course.degreeLevel = DegreeLevel.BACHELORS;
                    else if (lowerFileName.contains("ii stopnia")) course.degreeLevel = DegreeLevel.MASTERS;
                    else if (lowerFileName.contains("jednolite")) course.degreeLevel = DegreeLevel.LONG_MASTERS;
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.processFolderFiles", "Error processing folder files: " + e.getMessage());
        }
    }

    // helper methods

    public static String getScheduleFileUrl(OkHttpClient client, String major, DegreeLevel degreeLevel, StudyMode studyMode, int year) {
        Request request = new Request.Builder().url(PUW.homeUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            Document doc = Jsoup.parse(response.body().string());
            Elements courseLinks = doc.select("a[title*='Strefa studenta']");
            
            String courseUrl = null;
            for (Element link : courseLinks) {
                String title = link.attr("title").toLowerCase();
                if (title.contains(major.toLowerCase()) && 
                    title.contains(getDegreeLevelString(degreeLevel).toLowerCase())) {
                    courseUrl = link.attr("href");
                    break;
                }
            }
            if (courseUrl == null) return null;
            
            request = new Request.Builder().url(courseUrl).build();
            try (Response response2 = client.newCall(request).execute()) {
                if (!response2.isSuccessful() || response2.body() == null) return null;
                Document doc2 = Jsoup.parse(response2.body().string());
                Elements folderLinks = doc2.select("div.activityinstance > a");
                
                for (Element folderLink : folderLinks) {
                    String folderName = folderLink.select("span.instancename").text().toLowerCase();
                    if (!folderName.contains("plany zajęć")) continue;
                    
                    String folderUrl = folderLink.attr("href");
                    Request req3 = new Request.Builder().url(folderUrl).build();
                    try (Response response3 = client.newCall(req3).execute()) {
                        if (!response3.isSuccessful() || response3.body() == null) continue;
                        Document doc3 = Jsoup.parse(response3.body().string());
                        
                        // moodle file links contain pluginfile.php
                        Elements fileLinks = doc3.select("a[href*='pluginfile.php']");
                        for (Element fileLink : fileLinks) {
                            String fileName = fileLink.text().toLowerCase();
                            Element span = fileLink.selectFirst("span.fp-filename");
                            if (span != null) fileName = span.text().toLowerCase();
                            
                            if (fileName.endsWith(".xlsx")) {
                                Pattern pattern = Pattern.compile("(nst puw|nst|st)\\s+([IV]+)\\s*-", Pattern.CASE_INSENSITIVE);
                                Matcher matcher = pattern.matcher(fileName);
                                if (matcher.find()) {
                                    String fileModeStr = matcher.group(1).toLowerCase();
                                    int fileYear = romanToInt(matcher.group(2));
                                    
                                    StudyMode fileMode = StudyMode.fromString(fileModeStr);
                                    if (fileMode == null && determineStudyMode(folderName) != null) {
                                         fileMode = StudyMode.fromString(determineStudyMode(folderName));
                                    }
                                    
                                    if (fileMode == studyMode && fileYear == year) {
                                        String downloadUrl = fileLink.attr("href");
                                        if (!downloadUrl.contains("forcedownload=1")) {
                                            downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                                        }
                                        Logger.d("StudyPlanScraper.getScheduleFileUrl", "Resolved schedule file URL: " + downloadUrl);
                                        return downloadUrl;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.getScheduleFileUrl", "Error fetching schedule file url: " + e.getMessage());
        }
        return null;
    }

    private static String getDegreeLevelString(DegreeLevel level) {
        if (level == DegreeLevel.BACHELORS) return "I stopnia";
        if (level == DegreeLevel.MASTERS) return "II stopnia";
        if (level == DegreeLevel.LONG_MASTERS) return "jednolite";
        return "";
    }

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
            default: return 1;
        }
    }

    // find best matching course url on puw
    public static String findCourseUrl(OkHttpClient client, String subjectName, String lessonType) {
        if (subjectName == null || subjectName.trim().isEmpty()) {
            return null;
        }

        return searchBestCourse(client, subjectName, lessonType);
    }

    public static String getEnglishCourseUrl(OkHttpClient client) {
        return searchCourseByKeywords(client, "lektorat", "j. angielski");
    }

    public static String getStudentZoneUrl(OkHttpClient client) {
        return searchCourseByKeywords(client, "strefa studenta");
    }

    private static String searchBestCourse(OkHttpClient client, String subjectName, String lessonType) {
        try {
            Request request = new Request.Builder()
                    .url(PUW.baseUrl + "/my/courses.php")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    String foundUrl = extractBestCourseUrlFromHtml(html, subjectName, lessonType);
                    if (foundUrl != null) {
                        return foundUrl;
                    }
                }
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchBestCourse", "Error searching best course: " + e.getMessage());
        }

        // fallback to my page
        try {
            Request request = new Request.Builder()
                    .url(PUW.baseUrl + "/my/")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return extractBestCourseUrlFromHtml(response.body().string(), subjectName, lessonType);
                }
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchBestCourse", "Error searching best course fallback: " + e.getMessage());
        }

        return null;
    }

    private static String extractBestCourseUrlFromHtml(String html, String subjectName, String lessonType) {
        Document doc = Jsoup.parse(html, PUW.baseUrl);
        Elements courseLinks = doc.select("a[href*='course/view.php']");
        if (courseLinks.isEmpty()) return null;

        String cleanSubject = subjectName.replaceAll("[^a-zA-Zа-яА-Я0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ ]", " ").toLowerCase().trim();
        List<String> subjectWords = new ArrayList<>();
        for (String w : cleanSubject.split("\\s+")) {
            if (w.length() >= 3) {
                subjectWords.add(w);
            }
        }
        if (subjectWords.isEmpty()) return null;

        String cleanType = lessonType != null ? lessonType.replaceAll("[^a-zA-Zа-яА-Я0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ ]", " ").toLowerCase().trim() : null;

        String bestUrl = null;
        int maxScore = 0;

        for (Element link : courseLinks) {
            String courseText = link.text();
            if (courseText == null || courseText.trim().isEmpty()) continue;
            String normalizedCourseText = courseText.replaceAll("[^a-zA-Zа-яА-Я0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ ]", " ").toLowerCase().trim();

            int matchedSubjectWords = 0;
            for (String sw : subjectWords) {
                if (normalizedCourseText.contains(sw)) {
                    matchedSubjectWords++;
                }
            }

            // match at least 60% of subject words
            int requiredMatches = (subjectWords.size() <= 2) ? subjectWords.size() : (int) Math.ceil(subjectWords.size() * 0.6);
            if (matchedSubjectWords < requiredMatches) {
                continue;
            }

            int score = matchedSubjectWords * 10;

            // bonus for exact phrase match
            if (normalizedCourseText.contains(cleanSubject)) {
                score += 50;
            }

            // bonus if lesson type matches
            if (cleanType != null && !cleanType.isEmpty() && normalizedCourseText.contains(cleanType)) {
                score += 15;
            }

            // penalty if contains student zone
            if (normalizedCourseText.contains("strefa studenta") && !cleanSubject.contains("strefa studenta")) {
                score -= 40;
            }

            if (score > maxScore) {
                maxScore = score;
                bestUrl = link.attr("abs:href");
            }
        }

        return bestUrl;
    }

    // find course url by keywords
    private static String searchCourseByKeywords(OkHttpClient client, String... keywords) {
        try {
            Request request = new Request.Builder()
                    .url(PUW.baseUrl + "/my/courses.php")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return searchCourseByKeywordsOnUrl(client, PUW.baseUrl + "/my/", keywords);
                }
                String html = response.body().string();
                String foundUrl = extractCourseUrlFromHtml(html, keywords);
                if (foundUrl != null) {
                    return foundUrl;
                }
                return searchCourseByKeywordsOnUrl(client, PUW.baseUrl + "/my/", keywords);
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchCourseByKeywords", "Error searching course by keywords: " + e.getMessage());
            return null;
        }
    }

    private static String searchCourseByKeywordsOnUrl(OkHttpClient client, String pageUrl, String... keywords) {
        try {
            Request request = new Request.Builder().url(pageUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                return extractCourseUrlFromHtml(response.body().string(), keywords);
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchCourseByKeywordsOnUrl", "Error searching course on url " + pageUrl + ": " + e.getMessage());
            return null;
        }
    }

    private static String extractCourseUrlFromHtml(String html, String... keywords) {
        Document doc = Jsoup.parse(html, PUW.baseUrl);
        Elements courseLinks = doc.select("a[href*='course/view.php']");
        for (Element link : courseLinks) {
            String text = link.text().toLowerCase();
            boolean allMatch = true;
            for (String kw : keywords) {
                if (kw != null && !kw.isEmpty()) {
                    if (!text.contains(kw.toLowerCase())) {
                        allMatch = false;
                        break;
                    }
                }
            }
            if (allMatch) {
                return link.attr("abs:href");
            }
        }
        return null;
    }
}