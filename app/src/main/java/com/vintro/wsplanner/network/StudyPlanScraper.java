package com.vintro.wsplanner.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// scrapes puw moodle pages for courses, schedules, student zone links, and metadata
public class StudyPlanScraper {
    private static final String COURSES_PAGE_URL = PUW.baseUrl + "my/courses.php";
    private static final String URL_CACHE_PREFS = "puw_url_cache";
    private static final long URL_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;

    // safely resolve absolute url from an anchor element with fallback for unit tests without base uri
    private static String resolveHref(Element link) {
        if (link == null) return "";
        String abs = link.attr("abs:href");
        if (abs != null && !abs.trim().isEmpty()) {
            return abs.trim();
        }
        String raw = link.attr("href");
        return raw != null ? raw.trim() : "";
    }

    // unified schedule folder name check shared by onboarding and schedule download
    private static boolean isScheduleFolderName(String folderNameLower) {
        if (folderNameLower == null) return false;
        return folderNameLower.contains("plany zajęć")
                || folderNameLower.contains("plan zajęć")
                || folderNameLower.contains("rozklad")
                || folderNameLower.contains("rozkład")
                || folderNameLower.contains("harmonogram");
    }

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
        teacher = findSeminarTeacherInUrl(client, COURSES_PAGE_URL);
        return teacher;
    }

    private static String findSeminarTeacherInUrl(OkHttpClient client, String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            Document doc = Jsoup.parse(response.body().string(), url);

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
            Logger.e("StudyPlanScraper.findSeminarTeacherInUrl", "Error fetching seminar teacher from " + url + ": " + Logger.formatException(e));
        }
        return null;
    }

    public static Integer getEnglishGroup(OkHttpClient client) {
        Request request = new Request.Builder().url(PUW.homeUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            Document doc = Jsoup.parse(response.body().string(), PUW.homeUrl);

            // find links with title attribute
            Elements courseLinks = doc.select("a[title]");

            for (Element link : courseLinks) {
                String title = link.attr("title");

                // check if english course
                if (title.toLowerCase().contains("język angielski") && title.toLowerCase().contains("lektorat")) {
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
            Logger.e("StudyPlanScraper.getEnglishGroup", "Error fetching English group: " + Logger.formatException(e));
        }

        return null;
    }

    // get available courses from home page
    public static List<ParsedCourse> getAvailableCourses(OkHttpClient client) {
        Logger.d("StudyPlanScraper.getAvailableCourses", "Fetching available courses from PUW home page");
        Request request = new Request.Builder().url(PUW.homeUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Logger.e("StudyPlanScraper.getAvailableCourses", "Home page request failed with code: " + response.code());
                return new ArrayList<>();
            }
            String html = response.body().string();
            return parseAvailableCoursesFromHtml(html, client);
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.getAvailableCourses", "Error parsing courses: " + Logger.formatException(e));
            return new ArrayList<>();
        }
    }

    static List<ParsedCourse> parseAvailableCoursesFromHtml(String html) {
        return parseAvailableCoursesFromHtml(html, null);
    }

    static List<ParsedCourse> parseAvailableCoursesFromHtml(String html, OkHttpClient client) {
        List<ParsedCourse> courses = new ArrayList<>();
        if (html == null) return courses;
        Document doc = Jsoup.parse(html, PUW.baseUrl);

        // find student zone links case-insensitively
        Elements allCourseLinks = doc.select("a[title*='Strefa studenta'], a[title*='strefa studenta']");
        List<Element> regularCourseLinks = new ArrayList<>();
        for (Element el : allCourseLinks) {
            String title = el.attr("title").toLowerCase();
            if (!title.contains("rok zerowy") && !title.contains("uczestnika")) {
                regularCourseLinks.add(el);
            }
        }

        if (!regularCourseLinks.isEmpty()) {
            Logger.d("StudyPlanScraper.parseAvailableCoursesFromHtml", "Found " + regularCourseLinks.size() + " regular student zone link(s)");
            for (Element link : regularCourseLinks) {
                ParsedCourse course = new ParsedCourse();
                course.courseUrl = resolveHref(link);

                String title = link.attr("title");
                String[] parts = title.split(" - ");

                if (parts.length >= 2) {
                    course.fieldOfStudy = parts[1].replace("kierunek", "").trim();
                }

                if (parts.length >= 3) {
                    course.degreeLevel = parseDegreeLevel(parts[2]);
                }

                if (client != null && !course.courseUrl.isEmpty()) {
                    fetchCourseDetails(client, course);
                }
                courses.add(course);
            }
        } else {
            // fallback for preparatory program
            Logger.d("StudyPlanScraper.parseAvailableCoursesFromHtml", "No regular student zones found, checking Rok Zerowy links");
            Elements zeroYearLinks = doc.select("a[title*='ROK ZEROWY'], a[title*='Rok zerowy'], a[title*='rok zerowy'], a[title*='Strefa uczestnika'], a[title*='strefa uczestnika']");
            if (zeroYearLinks.isEmpty()) {
                for (Element a : doc.select("a[href*='course/view.php']")) {
                    String t = (a.attr("title") + " " + a.text()).toLowerCase();
                    if (t.contains("rok zerowy") || t.contains("uczestnika")) {
                        zeroYearLinks.add(a);
                        break;
                    }
                }
            }

            if (!zeroYearLinks.isEmpty()) {
                Element link = zeroYearLinks.first();
                String href = resolveHref(link);
                String title = link.attr("title");
                Logger.i("StudyPlanScraper.parseAvailableCoursesFromHtml", "Rok Zerowy participant zone detected: title=" + title + ", url=" + href);
                ParsedCourse course = new ParsedCourse();
                course.courseUrl = href;
                course.fieldOfStudy = "Rok zerowy";
                course.degreeLevel = DegreeLevel.ZERO_YEAR;
                course.availableModesAndYears.put("st", new ArrayList<>(List.of(1)));

                if (client != null && !course.courseUrl.isEmpty()) {
                    fetchCourseDetails(client, course);
                }
                course.availableModesAndYears.putIfAbsent("st", new ArrayList<>());
                if (!course.availableModesAndYears.get("st").contains(1)) {
                    course.availableModesAndYears.get("st").add(1);
                }
                courses.add(course);
            } else {
                Logger.d("StudyPlanScraper.parseAvailableCoursesFromHtml", "No Rok Zerowy participant links found on page");
            }
        }
        Logger.i("StudyPlanScraper.getAvailableCourses", "Parsed " + courses.size() + " available course(s): " + courses.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return courses;
    }

    // check course folders
    private static void fetchCourseDetails(OkHttpClient client, ParsedCourse course) {
        if (course.courseUrl == null || course.courseUrl.isEmpty()) return;
        Request request = new Request.Builder().url(course.courseUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string(), course.courseUrl);
            Elements folderLinks = doc.select("div.activityinstance > a, a[href*='folder/view.php']");

            boolean isZeroYear = (course.degreeLevel == DegreeLevel.ZERO_YEAR);

            for (Element folderLink : folderLinks) {
                String folderName = folderLink.select("span.instancename").text().toLowerCase();
                if (folderName.isEmpty()) folderName = folderLink.text().toLowerCase();

                if (!isZeroYear && !isScheduleFolderName(folderName) && !folderName.contains("rok zerowy")) {
                    continue;
                }

                String folderMode = determineStudyMode(folderName);
                String folderUrl = resolveHref(folderLink);
                if (!folderUrl.isEmpty()) {
                    processFolderFiles(client, folderUrl, course, folderMode);
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.fetchCourseDetails", "Error fetching course details: " + Logger.formatException(e));
        }
    }

    // parse xlsx files for study year and mode
    private static void processFolderFiles(OkHttpClient client, String folderUrl, ParsedCourse course, String folderMode) {
        Request request = new Request.Builder().url(folderUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            Document doc = Jsoup.parse(response.body().string(), folderUrl);
            Elements files = doc.select("span.fp-filename, a[href*='pluginfile.php']");

            for (Element file : files) {
                String fileName = file.text();
                if (!fileName.endsWith(".xlsx")) continue;

                if (course.degreeLevel == DegreeLevel.ZERO_YEAR) {
                    Logger.d("StudyPlanScraper.processFolderFiles", "Detected Rok Zerowy schedule file: " + fileName);
                    course.availableModesAndYears.putIfAbsent("st", new ArrayList<>());
                    if (!course.availableModesAndYears.get("st").contains(1)) {
                        course.availableModesAndYears.get("st").add(1);
                    }
                    continue;
                }

                Pattern pattern = Pattern.compile("(nst puw|nst|st)\\s+([IV]+)\\s*-", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(fileName);

                if (matcher.find()) {
                    String fileMode = matcher.group(1).toLowerCase();
                    String romanYear = matcher.group(2);
                    int year = romanToInt(romanYear);

                    String effectiveMode = (folderMode != null) ? folderMode : fileMode;

                    course.availableModesAndYears.putIfAbsent(effectiveMode, new ArrayList<>());
                    if (!course.availableModesAndYears.get(effectiveMode).contains(year)) {
                        course.availableModesAndYears.get(effectiveMode).add(year);
                    }
                }

                if (course.degreeLevel == null) {
                    String lowerFileName = fileName.toLowerCase();
                    if (lowerFileName.contains("i stopnia")) course.degreeLevel = DegreeLevel.BACHELORS;
                    else if (lowerFileName.contains("ii stopnia")) course.degreeLevel = DegreeLevel.MASTERS;
                    else if (lowerFileName.contains("jednolite")) course.degreeLevel = DegreeLevel.LONG_MASTERS;
                    else if (lowerFileName.contains("rok zerowy") || lowerFileName.contains("uczestnika")) course.degreeLevel = DegreeLevel.ZERO_YEAR;
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.processFolderFiles", "Error processing folder files: " + Logger.formatException(e));
        }
    }

    public static String getScheduleFileUrl(OkHttpClient client, String major, DegreeLevel degreeLevel, StudyMode studyMode, int year) {
        Request request = new Request.Builder().url(PUW.homeUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            Document doc = Jsoup.parse(response.body().string(), PUW.homeUrl);

            boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR)
                    || (major != null && major.toLowerCase().contains("rok zerowy"));

            String courseUrl = null;
            if (isZeroYear) {
                Logger.i("StudyPlanScraper.getScheduleFileUrl", "Resolving schedule file for Rok Zerowy (major=" + major + ")");
                Elements zeroLinks = doc.select("a[title*='ROK ZEROWY'], a[title*='Rok zerowy'], a[title*='rok zerowy'], a[title*='Strefa uczestnika'], a[title*='strefa uczestnika']");
                if (!zeroLinks.isEmpty()) {
                    courseUrl = resolveHref(zeroLinks.first());
                } else {
                    for (Element link : doc.select("a[href*='course/view.php']")) {
                        String t = (link.attr("title") + " " + link.text()).toLowerCase();
                        if (t.contains("rok zerowy") || t.contains("uczestnika")) {
                            courseUrl = resolveHref(link);
                            break;
                        }
                    }
                }
                Logger.d("StudyPlanScraper.getScheduleFileUrl", "Rok Zerowy course URL resolved: " + courseUrl);
            } else {
                Elements courseLinks = doc.select("a[title*='Strefa studenta'], a[title*='strefa studenta']");
                for (Element link : courseLinks) {
                    String title = link.attr("title").toLowerCase();
                    if (major != null && title.contains(major.toLowerCase()) &&
                            title.contains(getDegreeLevelString(degreeLevel).toLowerCase())) {
                        courseUrl = resolveHref(link);
                        break;
                    }
                }
            }
            if (courseUrl == null || courseUrl.isEmpty()) {
                Logger.w("StudyPlanScraper.getScheduleFileUrl", "No course URL found for major=" + major + ", degreeLevel=" + degreeLevel);
                return null;
            }

            request = new Request.Builder().url(courseUrl).build();
            try (Response response2 = client.newCall(request).execute()) {
                if (!response2.isSuccessful() || response2.body() == null) return null;
                Document doc2 = Jsoup.parse(response2.body().string(), courseUrl);

                if (isZeroYear) {
                    Elements directLinks = doc2.select("a[href*='pluginfile.php']");
                    Logger.d("StudyPlanScraper.getScheduleFileUrl", "Scanning " + directLinks.size() + " direct file links in Rok Zerowy course");
                    String fallbackDirectUrl = null;
                    for (Element link : directLinks) {
                        String name = link.text();
                        Element span = link.selectFirst("span.fp-filename");
                        if (span != null) name = span.text();

                        Logger.d("StudyPlanScraper.getScheduleFileUrl", "Inspecting file link: " + name);
                        if (isRokZerowyScheduleFile(name)) {
                            String downloadUrl = resolveHref(link);
                            if (!downloadUrl.isEmpty()) {
                                if (!downloadUrl.contains("forcedownload=1")) {
                                    downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                                }
                                Logger.i("StudyPlanScraper.getScheduleFileUrl", "Resolved Rok Zerowy direct schedule file URL: " + downloadUrl);
                                return downloadUrl;
                            }
                        } else if (name.toLowerCase().endsWith(".xlsx") && fallbackDirectUrl == null) {
                            String downloadUrl = resolveHref(link);
                            if (!downloadUrl.isEmpty()) {
                                if (!downloadUrl.contains("forcedownload=1")) {
                                    downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                                }
                                fallbackDirectUrl = downloadUrl;
                            }
                        }
                    }
                    if (fallbackDirectUrl != null) {
                        Logger.i("StudyPlanScraper.getScheduleFileUrl", "Resolved Rok Zerowy fallback direct schedule file URL: " + fallbackDirectUrl);
                        return fallbackDirectUrl;
                    }
                }

                Elements folderLinks = doc2.select("div.activityinstance > a, a[href*='folder/view.php']");

                for (Element folderLink : folderLinks) {
                    String folderName = folderLink.select("span.instancename").text().toLowerCase();
                    if (folderName.isEmpty()) folderName = folderLink.text().toLowerCase();

                    if (!isZeroYear && !isScheduleFolderName(folderName)) continue;

                    String folderUrl = resolveHref(folderLink);
                    if (folderUrl.isEmpty()) continue;
                    Request req3 = new Request.Builder().url(folderUrl).build();
                    try (Response response3 = client.newCall(req3).execute()) {
                        if (!response3.isSuccessful() || response3.body() == null) continue;
                        Document doc3 = Jsoup.parse(response3.body().string(), folderUrl);
                        String downloadUrl = extractScheduleFileFromFolderDoc(doc3, isZeroYear, studyMode, year, folderName);
                        if (downloadUrl != null) {
                            Logger.i("StudyPlanScraper.getScheduleFileUrl", "Resolved schedule file URL: " + downloadUrl);
                            return downloadUrl;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("StudyPlanScraper.getScheduleFileUrl", "Error fetching schedule file url: " + Logger.formatException(e));
        }
        return null;
    }

    static boolean isRokZerowyScheduleFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase().trim();
        if (!lower.endsWith(".xlsx")) return false;
        return lower.contains("rok zerowy")
                || lower.contains("semestr zimowy")
                || lower.contains("semestr letni")
                || lower.contains("semestr")
                || lower.contains("plan")
                || lower.contains("harmonogram")
                || lower.contains("rozklad");
    }

    static String extractScheduleFileFromFolderDoc(Document doc, boolean isZeroYear, StudyMode studyMode, int year, String folderName) {
        if (doc == null) return null;
        Elements fileLinks = doc.select("a[href*='pluginfile.php']");

        if (isZeroYear) {
            Logger.d("StudyPlanScraper.extractScheduleFileFromFolderDoc", "Inspecting folder files for Rok Zerowy (" + fileLinks.size() + " files)");
            String fallbackXlsxUrl = null;
            for (Element fileLink : fileLinks) {
                String fileName = fileLink.text();
                Element span = fileLink.selectFirst("span.fp-filename");
                if (span != null) fileName = span.text();

                Logger.d("StudyPlanScraper.extractScheduleFileFromFolderDoc", "Inspecting folder file: " + fileName);
                if (isRokZerowyScheduleFile(fileName)) {
                    String downloadUrl = resolveHref(fileLink);
                    if (!downloadUrl.isEmpty()) {
                        if (!downloadUrl.contains("forcedownload=1")) {
                            downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                        }
                        Logger.i("StudyPlanScraper.extractScheduleFileFromFolderDoc", "Matched Rok Zerowy schedule file: " + fileName + " -> " + downloadUrl);
                        return downloadUrl;
                    }
                } else if (fileName.toLowerCase().endsWith(".xlsx") && fallbackXlsxUrl == null) {
                    String downloadUrl = resolveHref(fileLink);
                    if (!downloadUrl.isEmpty()) {
                        if (!downloadUrl.contains("forcedownload=1")) {
                            downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                        }
                        fallbackXlsxUrl = downloadUrl;
                    }
                }
            }
            if (fallbackXlsxUrl != null) {
                Logger.i("StudyPlanScraper.extractScheduleFileFromFolderDoc", "Using fallback xlsx URL for Rok Zerowy: " + fallbackXlsxUrl);
            }
            return fallbackXlsxUrl;
        }

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
                    if (fileMode == null && folderName != null && determineStudyMode(folderName) != null) {
                        fileMode = StudyMode.fromString(determineStudyMode(folderName));
                    }

                    if (fileMode == studyMode && fileYear == year) {
                        String downloadUrl = resolveHref(fileLink);
                        if (!downloadUrl.isEmpty()) {
                            if (!downloadUrl.contains("forcedownload=1")) {
                                downloadUrl += (downloadUrl.contains("?") ? "&" : "?") + "forcedownload=1";
                            }
                            return downloadUrl;
                        }
                    }
                }
            }
        }
        return null;
    }

    static String getDegreeLevelString(DegreeLevel level) {
        if (level == DegreeLevel.BACHELORS) return "I stopnia";
        if (level == DegreeLevel.MASTERS) return "II stopnia";
        if (level == DegreeLevel.LONG_MASTERS) return "jednolite";
        if (level == DegreeLevel.ZERO_YEAR) return "rok zerowy";
        return "";
    }

    static DegreeLevel parseDegreeLevel(String text) {
        text = text.toLowerCase();
        if (text.contains("ii stopnia")) return DegreeLevel.MASTERS;
        if (text.contains("i stopnia")) return DegreeLevel.BACHELORS;
        if (text.contains("jednolite")) return DegreeLevel.LONG_MASTERS;
        if (text.contains("rok zerowy") || text.contains("uczestnika")) return DegreeLevel.ZERO_YEAR;
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

    // cached lookup for puw course urls with 7-day ttl
    public static String findCourseUrlCached(Context context, OkHttpClient client, String subjectName, String lessonType) {
        if (subjectName == null || subjectName.trim().isEmpty()) {
            return null;
        }
        String cacheKey = "course|" + subjectName.trim().toLowerCase(Locale.ROOT)
                + "|" + (lessonType != null ? lessonType.trim().toLowerCase(Locale.ROOT) : "");
        String cached = getCachedUrl(context, cacheKey);
        if (cached != null) {
            Logger.d("StudyPlanScraper.findCourseUrlCached", "Course URL cache HIT for '" + subjectName + "'");
            return cached;
        }
        String resolved = findCourseUrl(client, subjectName, lessonType);
        if (resolved != null && !resolved.isEmpty()) {
            putCachedUrl(context, cacheKey, resolved);
        }
        return resolved;
    }

    // cached lookup for student zone url with 7-day ttl
    public static String getStudentZoneUrlCached(Context context, OkHttpClient client, String major, DegreeLevel degreeLevel) {
        String cacheKey = "strefa|"
                + (major != null ? major.trim().toLowerCase(Locale.ROOT) : "")
                + "|" + (degreeLevel != null ? degreeLevel.name() : "");
        String cached = getCachedUrl(context, cacheKey);
        if (cached != null) {
            Logger.d("StudyPlanScraper.getStudentZoneUrlCached", "Student Zone URL cache HIT");
            return cached;
        }
        String resolved = getStudentZoneUrl(client, major, degreeLevel);
        if (resolved != null && !resolved.isEmpty()) {
            putCachedUrl(context, cacheKey, resolved);
        }
        return resolved;
    }

    // clear cached puw course and student zone urls
    public static void clearUrlCache(Context context) {
        if (context == null) return;
        context.getApplicationContext()
                .getSharedPreferences(URL_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        Logger.d("StudyPlanScraper.clearUrlCache", "Cleared cached PUW course and Student Zone URLs");
    }

    private static String getCachedUrl(Context context, String key) {
        if (context == null || key == null) return null;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(URL_CACHE_PREFS, Context.MODE_PRIVATE);
        String url = prefs.getString("u_" + key, null);
        long savedAt = prefs.getLong("t_" + key, 0L);
        if (url != null && !url.isEmpty() && (System.currentTimeMillis() - savedAt) < URL_CACHE_TTL_MS) {
            return url;
        }
        return null;
    }

    private static void putCachedUrl(Context context, String key, String url) {
        if (context == null || key == null || url == null) return;
        context.getApplicationContext()
                .getSharedPreferences(URL_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("u_" + key, url)
                .putLong("t_" + key, System.currentTimeMillis())
                .apply();
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
        return getStudentZoneUrl(client, null, null);
    }

    public static String getStudentZoneUrl(OkHttpClient client, String major, DegreeLevel degreeLevel) {
        try {
            Request request = new Request.Builder()
                    .url(COURSES_PAGE_URL)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    String foundUrl = extractStudentZoneUrlFromHtml(html, major, degreeLevel);
                    if (foundUrl != null) {
                        return foundUrl;
                    }
                }
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.getStudentZoneUrl", "Error fetching courses: " + Logger.formatException(e));
        }

        // fallback to dashboard
        try {
            Request request = new Request.Builder()
                    .url(PUW.homeUrl)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    return extractStudentZoneUrlFromHtml(html, major, degreeLevel);
                }
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.getStudentZoneUrl", "Error fetching my page: " + Logger.formatException(e));
        }

        return null;
    }

    static String extractStudentZoneUrlFromHtml(String html, String major, DegreeLevel degreeLevel) {
        if (html == null) return null;
        Document doc = Jsoup.parse(html, PUW.baseUrl);
        Elements courseLinks = doc.select("a[href*='course/view.php']");
        if (courseLinks.isEmpty()) return null;

        List<String> majorKeywords = new ArrayList<>();
        if (major != null && !major.trim().isEmpty()) {
            String cleanMajor = major.replaceAll("[^a-zA-Zа-яА-Я0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ ]", " ").toLowerCase().trim();
            for (String part : cleanMajor.split("\\s+")) {
                if (part.length() >= 3) {
                    majorKeywords.add(part);
                }
            }
        }

        List<String> degreeTokens = new ArrayList<>();
        if (degreeLevel == DegreeLevel.BACHELORS) {
            degreeTokens.add(" i ");
            degreeTokens.add(" 1 ");
            degreeTokens.add(" i stopnia");
        } else if (degreeLevel == DegreeLevel.MASTERS) {
            degreeTokens.add(" ii ");
            degreeTokens.add(" 2 ");
            degreeTokens.add(" ii stopnia");
        } else if (degreeLevel == DegreeLevel.LONG_MASTERS) {
            degreeTokens.add(" jednolite ");
            degreeTokens.add(" magisterskie ");
        }

        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR)
                || (major != null && major.toLowerCase().contains("rok zerowy"));

        if (isZeroYear) {
            Logger.d("StudyPlanScraper.extractStudentZoneUrlFromHtml", "Searching for Rok Zerowy participant zone in course links");
            for (Element link : courseLinks) {
                String rawText = link.text();
                if (rawText == null || rawText.trim().isEmpty()) continue;
                String lower = rawText.toLowerCase();
                if ((lower.contains("rok zerowy") || lower.contains("uczestnika")) && lower.contains("strefa")) {
                    String url = resolveHref(link);
                    if (!url.isEmpty()) {
                        Logger.i("StudyPlanScraper.extractStudentZoneUrlFromHtml", "Found Rok Zerowy student zone URL: " + url);
                        return url;
                    }
                }
            }
            for (Element link : courseLinks) {
                String rawText = link.text();
                if (rawText == null || rawText.trim().isEmpty()) continue;
                String lower = rawText.toLowerCase();
                if (lower.contains("rok zerowy") || lower.contains("uczestnika")) {
                    String url = resolveHref(link);
                    if (!url.isEmpty()) {
                        Logger.i("StudyPlanScraper.extractStudentZoneUrlFromHtml", "Found Rok Zerowy course URL: " + url);
                        return url;
                    }
                }
            }
            Logger.d("StudyPlanScraper.extractStudentZoneUrlFromHtml", "No Rok Zerowy course link matched");
        }

        String bestP1Url = null;
        String bestP2Url = null;
        String bestP3Url = null;
        String bestP4Url = null;

        for (Element link : courseLinks) {
            String rawText = link.text();
            if (rawText == null || rawText.trim().isEmpty()) continue;
            String paddedText = " " + rawText.toLowerCase().replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ") + " ";

            if (!paddedText.contains("strefa")) {
                continue;
            }

            boolean isZeroYearLink = paddedText.contains("rok zerowy") || paddedText.contains("uczestnika");
            if (!isZeroYear && isZeroYearLink) {
                continue;
            }

            boolean matchesMajor = false;
            if (!majorKeywords.isEmpty()) {
                for (String kw : majorKeywords) {
                    if (paddedText.contains(kw)) {
                        matchesMajor = true;
                        break;
                    }
                }
            }

            boolean matchesDegree = false;
            if (!degreeTokens.isEmpty()) {
                for (String dt : degreeTokens) {
                    if (paddedText.contains(dt)) {
                        matchesDegree = true;
                        break;
                    }
                }
            }

            String url = resolveHref(link);
            if (url.isEmpty()) continue;

            if (matchesMajor && matchesDegree) {
                if (bestP1Url == null) bestP1Url = url;
            } else if (matchesMajor) {
                if (bestP2Url == null) bestP2Url = url;
            } else if (paddedText.contains("studenta")) {
                if (bestP3Url == null) bestP3Url = url;
            } else {
                if (bestP4Url == null) bestP4Url = url;
            }
        }

        if (bestP1Url != null) return bestP1Url;
        if (bestP2Url != null) return bestP2Url;
        if (bestP3Url != null) return bestP3Url;
        return bestP4Url;
    }

    private static String searchBestCourse(OkHttpClient client, String subjectName, String lessonType) {
        try {
            Request request = new Request.Builder()
                    .url(COURSES_PAGE_URL)
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
            Logger.e("StudyPlanScraper.searchBestCourse", "Error searching best course: " + Logger.formatException(e));
        }

        // fallback to dashboard
        try {
            Request request = new Request.Builder()
                    .url(PUW.homeUrl)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return extractBestCourseUrlFromHtml(response.body().string(), subjectName, lessonType);
                }
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchBestCourse", "Error searching best course fallback: " + Logger.formatException(e));
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

            int requiredMatches = (subjectWords.size() <= 2) ? subjectWords.size() : (int) Math.ceil(subjectWords.size() * 0.6);
            if (matchedSubjectWords < requiredMatches) {
                continue;
            }

            int score = matchedSubjectWords * 10;

            if (normalizedCourseText.contains(cleanSubject)) {
                score += 50;
            }

            if (cleanType != null && !cleanType.isEmpty() && normalizedCourseText.contains(cleanType)) {
                score += 15;
            }

            if (normalizedCourseText.contains("strefa studenta") && !cleanSubject.contains("strefa studenta")) {
                score -= 40;
            }

            if (score > maxScore) {
                maxScore = score;
                bestUrl = resolveHref(link);
            }
        }

        return bestUrl;
    }

    private static String searchCourseByKeywords(OkHttpClient client, String... keywords) {
        try {
            Request request = new Request.Builder()
                    .url(COURSES_PAGE_URL)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return searchCourseByKeywordsOnUrl(client, PUW.homeUrl, keywords);
                }
                String html = response.body().string();
                String foundUrl = extractCourseUrlFromHtml(html, keywords);
                if (foundUrl != null) {
                    return foundUrl;
                }
                return searchCourseByKeywordsOnUrl(client, PUW.homeUrl, keywords);
            }
        } catch (IOException e) {
            Logger.e("StudyPlanScraper.searchCourseByKeywords", "Error searching course by keywords: " + Logger.formatException(e));
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
            Logger.e("StudyPlanScraper.searchCourseByKeywordsOnUrl", "Error searching course on url " + pageUrl + ": " + Logger.formatException(e));
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
                String resolved = resolveHref(link);
                return resolved.isEmpty() ? null : resolved;
            }
        }
        return null;
    }
}
