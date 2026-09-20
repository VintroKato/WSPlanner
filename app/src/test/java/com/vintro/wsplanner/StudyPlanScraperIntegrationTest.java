package com.vintro.wsplanner;

import org.junit.Test;
import static org.junit.Assert.*;

import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StudyPlanScraperIntegrationTest {

    // Укажите реальные данные от аккаунта PUW WSPA
    private static final String TEST_LOGIN = "pan.tyomych@gmail.com";
    private static final String TEST_PASSWORD = "Playerekeke_2";

    @Test
    public void testGetAvailableCourses_RealSite() throws Exception {
        // 1. Инициализация HTTP-клиента с поддержкой сессий (Cookie)
        Map<String, List<Cookie>> cookieStore = new HashMap<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : List.of();
                    }
                })
                .build();

        // 2. Авторизация на сайте (имитация PUW.loginWithClient)
        RequestBody formBody = new FormBody.Builder()
                .add("username", TEST_LOGIN)
                .add("password", TEST_PASSWORD)
                .build();

        Request loginRequest = new Request.Builder()
                .url(PUW.loginUrl)
                .post(formBody)
                .build();

        try (Response loginResponse = client.newCall(loginRequest).execute()) {
            assertTrue("Ожидается успешный HTTP-ответ при логине (200 OK)", loginResponse.isSuccessful());
            assertEquals("Ожидается редирект на главную страницу после логина",
                    PUW.homeUrl, loginResponse.request().url().toString());
        }

        // 3. Запуск каскадного парсинга
        List<ParsedCourse> courses = StudyPlanScraper.getAvailableCourses(client);

        // 4. Проверка базовых условий
        assertNotNull("Метод не должен возвращать null", courses);
        assertFalse("Список найденных направлений не должен быть пустым", courses.isEmpty());

        // 5. Вывод результатов в консоль для визуальной отладки
        System.out.println("Всего найдено направлений (Strefa studenta): " + courses.size());

        for (ParsedCourse course : courses) {
            System.out.println("--------------------------------------------------");
            System.out.println("Направление (kierunek): " + course.fieldOfStudy);
            System.out.println("Уровень (stopień): " + course.degreeLevel);
            System.out.println("URL курса: " + course.courseUrl);
            System.out.println("Доступные режимы и года: " + course.availableModesAndYears);

            // Базовые ассерты для каждого элемента
            assertNotNull("Название направления не распознано", course.fieldOfStudy);
            assertNotNull("URL курса не найден", course.courseUrl);
        }
    }
}