package com.vintro.wsplanner.ui.activities;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import android.view.ViewGroup;

import com.google.android.material.button.MaterialButton;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.network.PUW;
import java.util.Locale;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.services.TeacherEmailService;
import com.vintro.wsplanner.ui.adapters.TimelineAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

import java.util.List;

import okhttp3.OkHttpClient;

// subject details screen with notes and timeline
public class SubjectDetailsActivity extends AppCompatActivity {
    private static final String TAG = "SubjectDetailsActivity";
    private static final String PREFS_NOTES_NAME = "subject_notes_prefs";

    public static final String EXTRA_SUBJECT_NAME = "extra_subject_name";
    public static final String EXTRA_LESSON_TYPE = "extra_lesson_type";
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";

    private String subjectName;
    private String rawLessonType;
    private String notePrefKey;
    private String savedNoteText = "";

    // views
    private ImageButton buttonBack;
    private ImageButton buttonOpenCourse;
    private TextView heroBadgeLocation;
    private TextView heroBadgeType;
    private TextView heroSubjectTitle;
    private ViewGroup heroTeacherContainer;
    private TextView heroTeacherName;
    private View heroTeacherEmailRow;
    private TextView heroTeacherEmail;
    private ProgressBar teacherEmailProgress;
    private ImageButton buttonCopyTeacherEmail;
    private TextView heroRoomText;
    private ImageView heroLocationArrowIcon;
    private LinearLayout heroLocationRow;

    private String loadedTeacherEmail = null;

    // notes views
    private EditText editSubjectNotes;
    private View layoutSavedStatus;
    private MaterialButton buttonSaveNotes;

    // timeline views
    private TextView timelineProgressBadge;
    private RecyclerView recyclerTimeline;
    private TimelineAdapter timelineAdapter;

    private ScheduleRepository scheduleRepository;
    private SubjectDetails currentDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("SubjectDetailsActivity.onCreate", "SubjectDetailsActivity created for subject: '" + getIntent().getStringExtra(EXTRA_SUBJECT_NAME) + "'");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subject_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.subject_details_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);
        if (subjectName == null) subjectName = "";
        rawLessonType = getIntent().getStringExtra(EXTRA_LESSON_TYPE);
        if (rawLessonType == null) rawLessonType = "";

        notePrefKey = "note_" + subjectName.trim().toLowerCase() + "_" + rawLessonType.trim().toLowerCase();

        scheduleRepository = ScheduleRepository.getInstance(this);

        initViews();
        setupNotesLogic();
        setupTimelineRecyclerView();
        loadSubjectDetails();
    }

    private void initViews() {
        buttonBack = findViewById(R.id.button_back);
        buttonOpenCourse = findViewById(R.id.button_open_course);
        heroBadgeLocation = findViewById(R.id.hero_badge_location);
        heroBadgeType = findViewById(R.id.hero_badge_type);
        heroSubjectTitle = findViewById(R.id.hero_subject_title);
        heroTeacherContainer = findViewById(R.id.hero_teacher_container);
        heroTeacherName = findViewById(R.id.hero_teacher_name);
        heroTeacherEmailRow = findViewById(R.id.hero_teacher_email_row);
        heroTeacherEmail = findViewById(R.id.hero_teacher_email);
        teacherEmailProgress = findViewById(R.id.teacher_email_progress);
        buttonCopyTeacherEmail = findViewById(R.id.button_copy_teacher_email);
        heroRoomText = findViewById(R.id.hero_room_text);
        heroLocationArrowIcon = findViewById(R.id.hero_location_arrow_icon);
        heroLocationRow = findViewById(R.id.hero_location_row);

        // set title right away
        if (subjectName != null && !subjectName.trim().isEmpty()) {
            heroSubjectTitle.setText(subjectName.trim());
        }
        if (rawLessonType != null && !rawLessonType.trim().isEmpty()) {
            heroBadgeType.setVisibility(View.VISIBLE);
            heroBadgeType.setText(rawLessonType.trim().toUpperCase(Locale.getDefault()));
            heroBadgeType.setBackgroundTintList(null);
            heroBadgeType.setBackgroundResource(R.drawable.bg_badge_lab);
            heroBadgeType.setTextColor(ContextCompat.getColor(this, R.color.badge_lab_text));
        }

        editSubjectNotes = findViewById(R.id.edit_subject_notes);
        layoutSavedStatus = findViewById(R.id.layout_saved_status);
        buttonSaveNotes = findViewById(R.id.button_save_notes);

        timelineProgressBadge = findViewById(R.id.timeline_progress_badge);
        recyclerTimeline = findViewById(R.id.recycler_timeline);

        buttonBack.setOnClickListener(v -> finish());
        buttonOpenCourse.setOnClickListener(v -> handleOpenPuwCourse());
        buttonCopyTeacherEmail.setOnClickListener(v -> copyTeacherEmailToClipboard());
        heroTeacherEmail.setOnClickListener(v -> handleTeacherEmailClick());
    }

    private void setupTimelineRecyclerView() {
        timelineAdapter = new TimelineAdapter(this);
        recyclerTimeline.setLayoutManager(new LinearLayoutManager(this));
        recyclerTimeline.setAdapter(timelineAdapter);
    }

    private void setupNotesLogic() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
        savedNoteText = prefs.getString(notePrefKey, "");
        editSubjectNotes.setText(savedNoteText);

        // save button disabled until edited
        buttonSaveNotes.setEnabled(false);
        buttonSaveNotes.setAlpha(0.4f);
        layoutSavedStatus.setVisibility(View.VISIBLE);

        editSubjectNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasChanges = !s.toString().equals(savedNoteText);
                buttonSaveNotes.setEnabled(hasChanges);
                buttonSaveNotes.setAlpha(hasChanges ? 1.0f : 0.4f);
                layoutSavedStatus.setVisibility(hasChanges ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        buttonSaveNotes.setOnClickListener(v -> saveNotes());
    }

    private void saveNotes() {
        String currentText = editSubjectNotes.getText().toString();
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
        prefs.edit().putString(notePrefKey, currentText).apply();

        savedNoteText = currentText;
        buttonSaveNotes.setEnabled(false);
        buttonSaveNotes.setAlpha(0.4f);
        layoutSavedStatus.setVisibility(View.VISIBLE);

        // hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void loadSubjectDetails() {
        scheduleRepository.getSubjectDetails(subjectName, rawLessonType, false, new ScheduleRepository.ScheduleCallback<SubjectDetails>() {
            @Override
            public void onSuccess(SubjectDetails result) {
                currentDetails = result;
                runOnUiThread(() -> renderSubjectDetails(result));
            }

            @Override
            public void onError(Exception e) {
                Logger.e("SubjectDetailsActivity.loadSubjectDetails", "Error loading subject details: " + e.getMessage());
            }
        });
    }

    private void renderSubjectDetails(SubjectDetails details) {
        if (details == null) return;

        // animate layout changes
        ViewGroup rootLayout = findViewById(R.id.subject_details_root);
        if (rootLayout != null) {
            Transition transition = new AutoTransition();
            transition.setDuration(250);
            TransitionManager.beginDelayedTransition(rootLayout, transition);
        }

        heroSubjectTitle.setText(details.getSubjectName());
        if (details.hasTeacher()) {
            heroTeacherContainer.setVisibility(View.VISIBLE);
            heroTeacherName.setText(details.getTeacherName().trim());
            setupTeacherEmail(details.getTeacherName().trim());
        } else {
            heroTeacherContainer.setVisibility(View.GONE);
        }

        LessonType type = details.getLessonType();
        String displayTypeName = !details.getRawLessonType().isEmpty() 
                ? details.getRawLessonType() 
                : (type != null && type != LessonType.INNE ? type.getDisplayName() : null);
        if (displayTypeName != null && !displayTypeName.trim().isEmpty() && !displayTypeName.equalsIgnoreCase("inne") && !displayTypeName.equalsIgnoreCase("other")) {
            heroBadgeType.setVisibility(View.VISIBLE);
            heroBadgeType.setText(displayTypeName.toUpperCase(Locale.getDefault()));
            heroBadgeType.setBackgroundTintList(null);
            heroBadgeType.setBackgroundResource(R.drawable.bg_badge_lab);
            heroBadgeType.setTextColor(ContextCompat.getColor(this, R.color.badge_lab_text));
        } else {
            heroBadgeType.setVisibility(View.GONE);
        }

        List<Lesson> lessons = details.getSemesterLessons();
        timelineAdapter.submitLessons(lessons);

        // progress badge
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int total = details.getTotalCount();
        int completed = details.getCompletedCount(now);
        int percent = details.getProgressPercentage(now);
        timelineProgressBadge.setText(getString(R.string.schedule_progress_format, completed, total, percent));
        timelineProgressBadge.setBackgroundTintList(null);
        timelineProgressBadge.setBackgroundResource(R.drawable.bg_badge_lab);
        timelineProgressBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_lab_text));
        timelineProgressBadge.setVisibility(View.VISIBLE);

        // setup location
        Location dominantLoc = details.getDominantLocation();
        if (dominantLoc == null && !lessons.isEmpty()) {
            dominantLoc = lessons.get(0).getLocation();
        }
        setupHeroLocation(dominantLoc);
    }

    private void setupHeroLocation(Location location) {
        LocationType locType = location != null ? location.getType() : LocationType.UCZELNIA;
        heroBadgeLocation.setText(getString(locType.getStringResId()).toUpperCase(Locale.getDefault()));
        heroBadgeLocation.setVisibility(View.VISIBLE);
        heroBadgeLocation.setBackgroundTintList(null);

        if (locType == LocationType.ONLINE) {
            heroBadgeLocation.setBackgroundResource(R.drawable.bg_badge_puw);
            heroBadgeLocation.setTextColor(ContextCompat.getColor(this, R.color.badge_puw_text));
        } else if (locType == LocationType.W_TERENIE) {
            heroBadgeLocation.setBackgroundResource(R.drawable.bg_badge_field);
            heroBadgeLocation.setTextColor(ContextCompat.getColor(this, R.color.badge_field_text));
        } else {
            heroBadgeLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
            heroBadgeLocation.setTextColor(ContextCompat.getColor(this, R.color.badge_wspa_text));
        }

        if (location != null && location.hasMapLink()) {
            heroRoomText.setText(location.getDisplayText());
            if (heroLocationArrowIcon != null) {
                heroLocationArrowIcon.setVisibility(View.VISIBLE);
            }
            View.OnClickListener mapsClick = v -> {
                String query = location.getMapQueryUrl();
                if (query != null) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(query)));
                    } catch (Exception ignored) {}
                }
            };
            heroLocationRow.setOnClickListener(mapsClick);
            heroLocationRow.setClickable(true);
        } else {
            heroRoomText.setText(location != null ? location.getDisplayText() : "Sala —");
            if (heroLocationArrowIcon != null) {
                heroLocationArrowIcon.setVisibility(View.GONE);
            }
            heroLocationRow.setOnClickListener(null);
            heroLocationRow.setClickable(false);
        }
    }

    private void setupTeacherEmail(String teacherName) {
        if (teacherName == null || teacherName.trim().isEmpty() || teacherName.equals("—") || teacherName.equalsIgnoreCase("Unknown Teacher")) {
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.GONE);
            loadedTeacherEmail = null;
            return;
        }

        TeacherEmailService emailService = TeacherEmailService.getInstance(this);

        // check cache first
        TeacherEmailService.TeacherEmailResult cachedResult = emailService.getCachedEmail(teacherName);
        if (cachedResult != null) {
            applyTeacherEmailResult(cachedResult);
            return;
        }

        // wait for transition before searching email
        if (heroTeacherContainer != null) {
            heroTeacherContainer.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (heroTeacherContainer != null) {
                    Transition transition = new AutoTransition();
                    transition.setDuration(200);
                    TransitionManager.beginDelayedTransition(heroTeacherContainer, transition);
                }

                if (heroTeacherEmailRow != null) {
                    heroTeacherEmailRow.setVisibility(View.VISIBLE);
                }
                if (teacherEmailProgress != null) {
                    teacherEmailProgress.setVisibility(View.VISIBLE);
                }
                if (heroTeacherEmail != null) {
                    heroTeacherEmail.setText(R.string.teacher_email_searching);
                    heroTeacherEmail.setAlpha(0.6f);
                }
                if (buttonCopyTeacherEmail != null) {
                    buttonCopyTeacherEmail.setVisibility(View.GONE);
                }
                loadedTeacherEmail = null;

                emailService.getTeacherEmail(teacherName, result -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (heroTeacherContainer != null) {
                        Transition transition = new AutoTransition();
                        transition.setDuration(200);
                        TransitionManager.beginDelayedTransition(heroTeacherContainer, transition);
                    }
                    applyTeacherEmailResult(result);
                });
            }, 300);
        }
    }

    private void applyTeacherEmailResult(TeacherEmailService.TeacherEmailResult result) {
        if (teacherEmailProgress != null) {
            teacherEmailProgress.setVisibility(View.GONE);
        }

        if (result != null && result.hasEmail) {
            loadedTeacherEmail = result.email;
            if (heroTeacherEmailRow != null) {
                heroTeacherEmailRow.setVisibility(View.VISIBLE);
            }
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(result.email);
                heroTeacherEmail.setAlpha(1.0f);
            }
            if (buttonCopyTeacherEmail != null) {
                buttonCopyTeacherEmail.setVisibility(View.VISIBLE);
            }
        } else {
            loadedTeacherEmail = null;
            if (heroTeacherEmailRow != null) {
                heroTeacherEmailRow.setVisibility(View.VISIBLE);
            }
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(R.string.teacher_email_not_found);
                heroTeacherEmail.setAlpha(0.5f);
            }
            if (buttonCopyTeacherEmail != null) {
                buttonCopyTeacherEmail.setVisibility(View.GONE);
            }
        }
    }

    private void copyTeacherEmailToClipboard() {
        if (loadedTeacherEmail != null && !loadedTeacherEmail.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Teacher email", loadedTeacherEmail);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, getString(R.string.teacher_email_copied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleTeacherEmailClick() {
        if (loadedTeacherEmail != null && !loadedTeacherEmail.isEmpty()) {
            try {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:" + loadedTeacherEmail));
                startActivity(emailIntent);
            } catch (ActivityNotFoundException e) {
                copyTeacherEmailToClipboard();
            }
        }
    }

    private void handleOpenPuwCourse() {
        Toast.makeText(this, getString(R.string.subject_opening_puw_course), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                OkHttpClient client = PUW.globalLogin(SubjectDetailsActivity.this);
                String foundCourseUrl = null;
                if (client != null) {
                    foundCourseUrl = StudyPlanScraper.findCourseUrl(client, subjectName, rawLessonType);
                }

                final String courseUrl = foundCourseUrl;
                String targetUrl = (courseUrl != null) ? courseUrl : "https://puw.wspa.pl/my/";

                // Security check: validate scheme and domain
                try {
                    Uri parsedUri = Uri.parse(targetUrl);
                    String scheme = parsedUri.getScheme();
                    String host = parsedUri.getHost();
                    if (!"https".equalsIgnoreCase(scheme) || host == null || (!host.equalsIgnoreCase("puw.wspa.pl") && !host.endsWith(".wspa.pl"))) {
                        Logger.w("SubjectDetailsActivity.handleOpenPuwCourse", "Blocked opening untrusted URL: " + targetUrl);
                        targetUrl = "https://puw.wspa.pl/my/";
                    }
                } catch (Exception e) {
                    targetUrl = "https://puw.wspa.pl/my/";
                }

                final String safeFinalUrl = targetUrl;

                runOnUiThread(() -> {
                    if (courseUrl == null) {
                        Toast.makeText(SubjectDetailsActivity.this, getString(R.string.subject_course_not_found), Toast.LENGTH_SHORT).show();
                    }
                    try {
                        Logger.i("SubjectDetailsActivity.handleOpenPuwCourse", "Opening course URL: " + safeFinalUrl);
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeFinalUrl));
                        startActivity(browserIntent);
                    } catch (Exception e) {
                        Logger.e("SubjectDetailsActivity.handleOpenPuwCourse", "Failed to open browser: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Logger.e("SubjectDetailsActivity.handleOpenPuwCourse", "Error in handleOpenPuwCourse: " + e.getMessage());
                runOnUiThread(() -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://puw.wspa.pl/my/")));
                    } catch (Exception ignored) {}
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // save notes on exit
        if (editSubjectNotes != null) {
            String currentText = editSubjectNotes.getText().toString();
            if (!currentText.equals(savedNoteText)) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
                prefs.edit().putString(notePrefKey, currentText).apply();
                savedNoteText = currentText;
            }
        }
    }
}
