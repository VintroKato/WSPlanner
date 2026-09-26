package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.ui.adapters.AllCoursesAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;

public class AllCoursesActivity extends AppCompatActivity {
    private static final String TAG = "AllCoursesActivity";
    private static final long DEBOUNCE_DELAY_MS = 500;

    private ScheduleRepository scheduleRepository;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private ImageButton buttonBack;
    private TextView textCoursesCount;
    private EditText editSearch;
    private ImageButton buttonClearSearch;
    private RecyclerView recyclerAllCourses;
    private ProgressBar progressLoading;
    private LinearLayout layoutEmptyState;

    private AllCoursesAdapter adapter;
    private final List<SubjectDetails> allCourses = new ArrayList<>();
    private String studentMajor = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("AllCoursesActivity.onCreate", "AllCoursesActivity created");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_courses);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.all_courses_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        scheduleRepository = ScheduleRepository.getInstance(this);
        studentMajor = PreferencesManager.getGlobalMajorPref(this);
        if (studentMajor == null) studentMajor = "";

        initViews();
        setupSearch();
        loadCourses();
    }

    private void initViews() {
        buttonBack = findViewById(R.id.button_back);
        textCoursesCount = findViewById(R.id.text_courses_count);
        editSearch = findViewById(R.id.edit_search);
        buttonClearSearch = findViewById(R.id.button_clear_search);
        recyclerAllCourses = findViewById(R.id.recycler_all_courses);
        progressLoading = findViewById(R.id.progress_loading);
        layoutEmptyState = findViewById(R.id.layout_empty_state);

        buttonBack.setOnClickListener(v -> finish());

        adapter = new AllCoursesAdapter(this, this::openStudentZone);
        recyclerAllCourses.setLayoutManager(new LinearLayoutManager(this));
        recyclerAllCourses.setAdapter(adapter);

        buttonClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            filterCourses("");
        });
    }

    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (buttonClearSearch != null) {
                    buttonClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                final String query = s.toString();
                searchRunnable = () -> filterCourses(query);
                searchHandler.postDelayed(searchRunnable, DEBOUNCE_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCourses() {
        Logger.d("AllCoursesActivity.loadCourses", "Loading all semester courses from repository");
        progressLoading.setVisibility(View.VISIBLE);
        recyclerAllCourses.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);

        scheduleRepository.getAllCourses(false, new ScheduleRepository.ScheduleCallback<List<SubjectDetails>>() {
            @Override
            public void onSuccess(List<SubjectDetails> result) {
                Logger.i("AllCoursesActivity.loadCourses", "Loaded " + (result != null ? result.size() : 0) + " semester courses");
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    allCourses.clear();
                    if (result != null) {
                        allCourses.addAll(result);
                    }
                    filterCourses(editSearch.getText().toString());
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e("AllCoursesActivity.loadCourses", "Error loading all courses (offline/no cache): " + e.getMessage());
                runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    filterCourses(editSearch.getText().toString());
                });
            }
        });
    }

    private void filterCourses(String rawQuery) {
        String cleanQuery = rawQuery != null ? rawQuery.trim() : "";

        if (cleanQuery.isEmpty()) {
            // Search is empty: Show Strefa Studenta first, then all courses
            adapter.setData(allCourses, true, studentMajor);
            updateVisibility(allCourses.size() > 0 || !studentMajor.isEmpty());
            updateCount(allCourses.size());
            return;
        }

        String normalizedQuery = normalizePolishText(cleanQuery);
        String[] tokens = normalizedQuery.split("\\s+");

        // Check if Strefa Studenta matches query
        String strefaSearchable = normalizePolishText("strefa studenta " + studentMajor);
        boolean strefaMatches = matchesAllTokens(strefaSearchable, tokens);

        // Filter regular courses
        List<SubjectDetails> filtered = new ArrayList<>();
        for (SubjectDetails course : allCourses) {
            String searchable = normalizePolishText(
                    course.getSubjectName() + " " +
                    (course.getRawLessonType() != null ? course.getRawLessonType() : "") + " " +
                    (course.getLessonType() != null ? course.getLessonType().getDisplayName() : "") + " " +
                    course.getTeacherName()
            );

            if (matchesAllTokens(searchable, tokens)) {
                filtered.add(course);
            }
        }

        boolean hasResults = strefaMatches || !filtered.isEmpty();
        adapter.setData(filtered, strefaMatches, studentMajor);
        updateVisibility(hasResults);
        updateCount(filtered.size());
    }

    private boolean matchesAllTokens(String searchableText, String[] tokens) {
        if (tokens == null || tokens.length == 0) return true;
        for (String token : tokens) {
            if (!token.isEmpty() && !searchableText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizePolishText(String input) {
        if (input == null) return "";
        String s = input.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ą': sb.append('a'); break;
                case 'ć': sb.append('c'); break;
                case 'ę': sb.append('e'); break;
                case 'ł': sb.append('l'); break;
                case 'ń': sb.append('n'); break;
                case 'ó': sb.append('o'); break;
                case 'ś': sb.append('s'); break;
                case 'ź':
                case 'ż': sb.append('z'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private void updateVisibility(boolean hasResults) {
        if (hasResults) {
            recyclerAllCourses.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        } else {
            recyclerAllCourses.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private void updateCount(int count) {
        if (textCoursesCount != null) {
            if (count > 0) {
                textCoursesCount.setVisibility(View.VISIBLE);
                textCoursesCount.setText(getString(R.string.all_courses_count_format, count));
            } else {
                textCoursesCount.setVisibility(View.GONE);
            }
        }
    }

    private void openStudentZone() {
        Toast.makeText(this, R.string.all_courses_opening_strefa, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            String url = null;
            try {
                OkHttpClient client = PUW.globalLogin(this);
                if (client != null) {
                    url = StudyPlanScraper.getStudentZoneUrl(client);
                }
            } catch (Exception e) {
                Logger.e("AllCoursesActivity.openStudentZone", "Error resolving student zone url: " + e.getMessage());
            }

            if (url == null || url.trim().isEmpty()) {
                url = PUW.baseUrl + "/my/";
            }

            final String finalUrl = url;
            runOnUiThread(() -> {
                try {
                    Logger.i("AllCoursesActivity.openStudentZone", "Opening student zone URL: " + finalUrl);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Logger.e("AllCoursesActivity.openStudentZone", "Error opening browser: " + e.getMessage());
                }
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}
