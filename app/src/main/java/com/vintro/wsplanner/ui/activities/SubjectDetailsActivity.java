package com.vintro.wsplanner.ui.activities;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.network.MoodleAutologinManager;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.services.TeacherEmailService;
import com.vintro.wsplanner.ui.adapters.TimelineAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.WidgetActionHelper;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;

// subject details screen with auto-saved notes and meetings timeline
public class SubjectDetailsActivity extends AppCompatActivity {
    private static final String TAG = "SubjectDetailsActivity";
    public static final String PREFS_NOTES_NAME = "subject_notes_prefs";
    private static final long NOTES_AUTOSAVE_DEBOUNCE_MS = 500L;

    public static final String EXTRA_SUBJECT_NAME = "extra_subject_name";
    public static final String EXTRA_LESSON_TYPE = "extra_lesson_type";
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";

    private String subjectName;
    private String rawLessonType;
    private String notePrefKey;
    private String legacyNotePrefKey;
    private String savedNoteText = "";

    private final Handler notesHandler = new Handler(Looper.getMainLooper());
    private Runnable autoSaveRunnable;

    private ImageButton buttonBack;
    private View buttonOpenCourse;
    private View contentOpenCourse;
    private ProgressBar progressOpenCourse;
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
    private LinearLayout heroLocationRow;

    private String currentTeacherName = null;
    private String loadedTeacherEmail = null;
    private String loadedTeacherProfileUrl = null;
    private boolean teacherEmailNetworkError = false;

    private EditText editSubjectNotes;
    private ImageView iconNotesStatus;
    private TextView textNotesStatus;

    private TextView timelineProgressBadge;
    private RecyclerView recyclerTimeline;
    private TimelineAdapter timelineAdapter;

    private ScheduleRepository scheduleRepository;

    // build canonical note preference key shared across schedule, courses, and details
    public static String buildCanonicalNoteKey(String subjectName, @Nullable LessonType resolvedType, @Nullable String rawLessonType) {
        String sub = subjectName != null ? subjectName.trim().toLowerCase(Locale.ROOT) : "";
        LessonType effectiveType = resolvedType;
        if (effectiveType == null) {
            effectiveType = (rawLessonType != null && !rawLessonType.trim().isEmpty())
                    ? LessonType.fromString(rawLessonType)
                    : LessonType.fromSubjectName(subjectName);
        }
        String typePart = effectiveType != null ? effectiveType.name().toLowerCase(Locale.ROOT) : "";
        return "note_" + sub + "_" + typePart;
    }

    // build legacy raw-type note preference key for backwards-compatible reads
    public static String buildLegacyNoteKey(String subjectName, @Nullable String rawLessonType) {
        String sub = subjectName != null ? subjectName.trim().toLowerCase(Locale.ROOT) : "";
        String raw = rawLessonType != null ? rawLessonType.trim().toLowerCase(Locale.ROOT) : "";
        return "note_" + sub + "_" + raw;
    }

    // check whether a non-empty student note exists for a subject under canonical or legacy key
    public static boolean hasSavedNote(Context context, String subjectName, @Nullable LessonType resolvedType, @Nullable String rawLessonType) {
        if (context == null || subjectName == null || subjectName.trim().isEmpty()) {
            return false;
        }
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
        String canonicalKey = buildCanonicalNoteKey(subjectName, resolvedType, rawLessonType);
        String canonicalVal = prefs.getString(canonicalKey, null);
        if (canonicalVal != null) {
            return !canonicalVal.trim().isEmpty();
        }
        String legacyKey = buildLegacyNoteKey(subjectName, rawLessonType);
        if (!legacyKey.equals(canonicalKey)) {
            String legacyVal = prefs.getString(legacyKey, null);
            return legacyVal != null && !legacyVal.trim().isEmpty();
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d(TAG + ".onCreate", "SubjectDetailsActivity created for subject: '" + getIntent().getStringExtra(EXTRA_SUBJECT_NAME) + "'");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        UIHelper.setupSystemBars(this);
        setContentView(R.layout.activity_subject_details);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.subject_details_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        subjectName = getIntent().getStringExtra(EXTRA_SUBJECT_NAME);
        if (subjectName == null || subjectName.isEmpty()) {
            subjectName = getIntent().getStringExtra("subject_name");
        }
        if (subjectName == null) subjectName = "";

        rawLessonType = getIntent().getStringExtra(EXTRA_LESSON_TYPE);
        if (rawLessonType == null || rawLessonType.isEmpty()) {
            rawLessonType = getIntent().getStringExtra("lesson_type");
        }
        if (rawLessonType == null) rawLessonType = "";

        LessonType initialResolvedType = !rawLessonType.trim().isEmpty()
                ? LessonType.fromString(rawLessonType)
                : LessonType.fromSubjectName(subjectName);
        notePrefKey = buildCanonicalNoteKey(subjectName, initialResolvedType, rawLessonType);
        legacyNotePrefKey = buildLegacyNoteKey(subjectName, rawLessonType);

        scheduleRepository = ScheduleRepository.getInstance(this);

        initViews();
        setupNotesLogic();
        setupTimelineRecyclerView();
        loadSubjectDetails();
    }

    private void initViews() {
        buttonBack = findViewById(R.id.button_back);
        buttonOpenCourse = findViewById(R.id.button_open_course);
        contentOpenCourse = findViewById(R.id.content_open_course);
        progressOpenCourse = findViewById(R.id.progress_open_course);
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
        heroLocationRow = findViewById(R.id.hero_location_row);

        if (subjectName != null && !subjectName.trim().isEmpty()) {
            heroSubjectTitle.setText(subjectName.trim());
        }
        LessonType initialType = !rawLessonType.trim().isEmpty()
                ? LessonType.fromString(rawLessonType)
                : LessonType.fromSubjectName(subjectName);
        bindHeroTypeBadge(initialType, rawLessonType);

        editSubjectNotes = findViewById(R.id.edit_subject_notes);
        iconNotesStatus = findViewById(R.id.icon_notes_status);
        textNotesStatus = findViewById(R.id.text_notes_status);

        timelineProgressBadge = findViewById(R.id.timeline_progress_badge);
        recyclerTimeline = findViewById(R.id.recycler_timeline);

        buttonBack.setOnClickListener(v -> finish());
        buttonOpenCourse.setOnClickListener(v -> handleOpenPuwCourse());
        TooltipCompat.setTooltipText(buttonOpenCourse, getString(R.string.subject_open_puw_course_tooltip));
        buttonCopyTeacherEmail.setOnClickListener(v -> copyTeacherEmailToClipboard());
        heroTeacherEmail.setOnClickListener(v -> handleTeacherEmailClick());
    }

    private void bindHeroTypeBadge(@Nullable LessonType resolvedType, @Nullable String rawType) {
        String displayTypeName = null;
        if (resolvedType != null && resolvedType != LessonType.INNE) {
            displayTypeName = resolvedType.getLocalizedDisplayName(this);
        } else if (rawType != null && !rawType.trim().isEmpty()) {
            String trimmed = rawType.trim();
            if (!trimmed.equalsIgnoreCase("inne") && !trimmed.equalsIgnoreCase("other")) {
                displayTypeName = trimmed;
            }
        }

        if (displayTypeName != null && !displayTypeName.trim().isEmpty()) {
            heroBadgeType.setVisibility(View.VISIBLE);
            heroBadgeType.setText(displayTypeName.toUpperCase(Locale.getDefault()));
            heroBadgeType.setBackgroundTintList(null);
            heroBadgeType.setBackgroundResource(R.drawable.bg_badge_type);
            heroBadgeType.setTextColor(UIHelper.getThemeColor(this, R.attr.badge_type_text));
        } else {
            heroBadgeType.setVisibility(View.GONE);
        }
    }

    private void setupTimelineRecyclerView() {
        timelineAdapter = new TimelineAdapter(this);
        timelineAdapter.setOnTimelineLessonClickListener(lesson -> {
            if (lesson == null || lesson.getDate() == null) return;
            Logger.i(TAG + ".timelineClick", "Jumping to schedule date from timeline: " + lesson.getDate());
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
            intent.putExtra(MainActivity.EXTRA_TARGET_DATE, lesson.getDate().toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        recyclerTimeline.setLayoutManager(new LinearLayoutManager(this));
        recyclerTimeline.setAdapter(timelineAdapter);
    }

    private void setupNotesLogic() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
        String loaded = prefs.getString(notePrefKey, null);
        if (loaded == null && legacyNotePrefKey != null && !legacyNotePrefKey.equals(notePrefKey)) {
            loaded = prefs.getString(legacyNotePrefKey, "");
        }
        savedNoteText = loaded != null ? loaded : "";
        editSubjectNotes.setText(savedNoteText);
        showNotesSavedState();

        editSubjectNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s != null ? s.toString() : "";
                if (autoSaveRunnable != null) {
                    notesHandler.removeCallbacks(autoSaveRunnable);
                }
                if (current.equals(savedNoteText)) {
                    showNotesSavedState();
                    return;
                }
                showNotesSavingState();
                autoSaveRunnable = () -> persistNotes(current);
                notesHandler.postDelayed(autoSaveRunnable, NOTES_AUTOSAVE_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showNotesSavingState() {
        if (iconNotesStatus != null) {
            iconNotesStatus.setVisibility(View.GONE);
        }
        if (textNotesStatus != null) {
            textNotesStatus.setText(R.string.subject_notes_saving);
            textNotesStatus.setTextColor(UIHelper.getThemeColor(this, R.attr.text_muted));
        }
    }

    private void showNotesSavedState() {
        int okColor = UIHelper.getThemeColor(this, R.attr.input_border_ok);
        if (iconNotesStatus != null) {
            iconNotesStatus.setVisibility(View.VISIBLE);
            iconNotesStatus.setImageTintList(android.content.res.ColorStateList.valueOf(okColor));
        }
        if (textNotesStatus != null) {
            textNotesStatus.setText(R.string.subject_notes_saved);
            textNotesStatus.setTextColor(okColor);
        }
    }

    private void persistNotes(String currentText) {
        if (currentText == null) currentText = "";
        if (currentText.equals(savedNoteText)) {
            showNotesSavedState();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTES_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (currentText.trim().isEmpty()) {
            editor.remove(notePrefKey);
        } else {
            editor.putString(notePrefKey, currentText);
        }
        if (legacyNotePrefKey != null && !legacyNotePrefKey.equals(notePrefKey) && prefs.contains(legacyNotePrefKey)) {
            editor.remove(legacyNotePrefKey);
        }
        editor.apply();
        savedNoteText = currentText;
        showNotesSavedState();
        Logger.i(TAG + ".persistNotes", "Auto-saved notes for subject '" + subjectName + "' (length=" + currentText.length() + ")");
    }

    private void loadSubjectDetails() {
        if (!scheduleRepository.isConfigurationComplete()) {
            Logger.d(TAG + ".loadSubjectDetails", "Student configuration incomplete, skipping details load");
            return;
        }
        Logger.d(TAG + ".loadSubjectDetails", "Loading details for subject: '" + subjectName + "'");
        scheduleRepository.getSubjectDetails(subjectName, rawLessonType, false, new ScheduleRepository.ScheduleCallback<SubjectDetails>() {
            @Override
            public void onSuccess(SubjectDetails result) {
                Logger.i(TAG + ".loadSubjectDetails", "Successfully loaded details for: '" + subjectName + "' (" + (result != null ? result.getTotalCount() : 0) + " lessons)");
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        renderSubjectDetails(result);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e(TAG + ".loadSubjectDetails", "Error loading subject details: " + Logger.formatException(e));
            }
        });
    }

    private void renderSubjectDetails(SubjectDetails details) {
        if (details == null) return;

        ViewGroup rootLayout = findViewById(R.id.subject_details_root);
        if (rootLayout != null) {
            Transition transition = new AutoTransition();
            transition.setDuration(220);
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

        bindHeroTypeBadge(details.getLessonType(), details.getRawLessonType());

        List<Lesson> lessons = details.getSemesterLessons();
        timelineAdapter.submitLessons(lessons);

        LocalDateTime now = LocalDateTime.now();
        int total = details.getTotalCount();
        int completed = details.getCompletedCount(now);
        int percent = details.getProgressPercentage(now);
        timelineProgressBadge.setText(getString(R.string.schedule_progress_format, completed, total, percent));
        timelineProgressBadge.setBackgroundTintList(null);
        timelineProgressBadge.setBackgroundResource(R.drawable.bg_badge_type);
        timelineProgressBadge.setTextColor(UIHelper.getThemeColor(this, R.attr.badge_type_text));
        timelineProgressBadge.setVisibility(View.VISIBLE);

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
            heroBadgeLocation.setTextColor(UIHelper.getThemeColor(this, R.attr.badge_puw_text));
        } else if (locType == LocationType.W_TERENIE) {
            heroBadgeLocation.setBackgroundResource(R.drawable.bg_badge_field);
            heroBadgeLocation.setTextColor(UIHelper.getThemeColor(this, R.attr.badge_field_text));
        } else {
            heroBadgeLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
            heroBadgeLocation.setTextColor(UIHelper.getThemeColor(this, R.attr.badge_wspa_text));
        }

        if (location != null && location.hasMapLink()) {
            heroRoomText.setText(location.getDisplayText());
            heroRoomText.setTextColor(UIHelper.getThemeColor(this, R.attr.email_link_text));
            View.OnClickListener mapsClick = v -> {
                String query = location.getMapQueryUrl();
                if (query != null) {
                    WidgetActionHelper.openLocationInMaps(this, query);
                }
            };
            heroLocationRow.setOnClickListener(mapsClick);
            heroLocationRow.setClickable(true);
        } else {
            heroRoomText.setText(location != null ? location.getDisplayText() : "—");
            heroRoomText.setTextColor(UIHelper.getThemeColor(this, R.attr.text_secondary));
            heroLocationRow.setOnClickListener(null);
            heroLocationRow.setClickable(false);
        }
    }

    private void setupTeacherEmail(String teacherName) {
        currentTeacherName = teacherName;
        loadedTeacherEmail = null;
        teacherEmailNetworkError = false;
        updateTeacherProfileLink(null);

        if (teacherName == null || teacherName.trim().isEmpty() || teacherName.equals("—") || teacherName.equalsIgnoreCase("Unknown Teacher")) {
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.GONE);
            return;
        }

        TeacherEmailService emailService = TeacherEmailService.getInstance(this);

        TeacherEmailService.TeacherEmailResult cachedResult = emailService.getCachedEmail(teacherName);
        if (cachedResult != null) {
            Logger.d(TAG + ".searchTeacherEmail", "Teacher email cache hit");
            applyTeacherEmailResult(cachedResult);
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Logger.w(TAG + ".searchTeacherEmail", "Device is offline and email not cached");
            teacherEmailNetworkError = true;
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.VISIBLE);
            if (teacherEmailProgress != null) teacherEmailProgress.setVisibility(View.GONE);
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(R.string.teacher_email_retry_hint);
                heroTeacherEmail.setTextColor(UIHelper.getThemeColor(this, R.attr.text_muted));
            }
            if (buttonCopyTeacherEmail != null) buttonCopyTeacherEmail.setVisibility(View.GONE);
            return;
        }

        if (heroTeacherContainer != null) {
            heroTeacherContainer.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;

                Transition transition = new AutoTransition();
                transition.setDuration(200);
                TransitionManager.beginDelayedTransition(heroTeacherContainer, transition);

                if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.VISIBLE);
                if (teacherEmailProgress != null) teacherEmailProgress.setVisibility(View.VISIBLE);
                if (heroTeacherEmail != null) {
                    heroTeacherEmail.setText(R.string.teacher_email_searching);
                    heroTeacherEmail.setTextColor(UIHelper.getThemeColor(this, R.attr.text_muted));
                }
                if (buttonCopyTeacherEmail != null) buttonCopyTeacherEmail.setVisibility(View.GONE);

                emailService.getTeacherEmail(teacherName, result -> {
                    if (isFinishing() || isDestroyed()) return;
                    Transition innerTransition = new AutoTransition();
                    innerTransition.setDuration(200);
                    TransitionManager.beginDelayedTransition(heroTeacherContainer, innerTransition);
                    applyTeacherEmailResult(result);
                });
            }, 300);
        }
    }

    private void updateTeacherProfileLink(@Nullable String profileUrl) {
        loadedTeacherProfileUrl = (profileUrl != null && !profileUrl.trim().isEmpty()) ? profileUrl.trim() : null;
        if (heroTeacherName == null) return;
        if (loadedTeacherProfileUrl != null) {
            heroTeacherName.setTextColor(UIHelper.getThemeColor(this, R.attr.email_link_text));
            heroTeacherName.setOnClickListener(v -> openTeacherProfile());
            heroTeacherName.setClickable(true);
            TooltipCompat.setTooltipText(heroTeacherName, getString(R.string.teacher_open_profile));
        } else {
            heroTeacherName.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
            heroTeacherName.setOnClickListener(null);
            heroTeacherName.setClickable(false);
            TooltipCompat.setTooltipText(heroTeacherName, null);
        }
    }

    private void openTeacherProfile() {
        if (loadedTeacherProfileUrl == null || loadedTeacherProfileUrl.isEmpty()) return;
        try {
            Logger.i(TAG, "Opening teacher WSPA profile URL in browser");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(loadedTeacherProfileUrl)));
        } catch (Exception e) {
            Logger.w(TAG, "Failed opening teacher profile URL: " + Logger.formatException(e));
        }
    }

    private void applyTeacherEmailResult(TeacherEmailService.TeacherEmailResult result) {
        if (teacherEmailProgress != null) {
            teacherEmailProgress.setVisibility(View.GONE);
        }

        loadedTeacherEmail = null;
        teacherEmailNetworkError = false;
        updateTeacherProfileLink(result != null ? result.profileUrl : null);

        if (result != null && result.hasEmail) {
            loadedTeacherEmail = result.email;
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.VISIBLE);
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(result.email);
                heroTeacherEmail.setTextColor(UIHelper.getThemeColor(this, R.attr.email_link_text));
            }
            if (buttonCopyTeacherEmail != null) buttonCopyTeacherEmail.setVisibility(View.VISIBLE);
        } else if (result != null && result.isNetworkError) {
            teacherEmailNetworkError = true;
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.VISIBLE);
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(R.string.teacher_email_retry_hint);
                heroTeacherEmail.setTextColor(UIHelper.getThemeColor(this, R.attr.text_muted));
            }
            if (buttonCopyTeacherEmail != null) buttonCopyTeacherEmail.setVisibility(View.GONE);
        } else {
            if (heroTeacherEmailRow != null) heroTeacherEmailRow.setVisibility(View.VISIBLE);
            if (heroTeacherEmail != null) {
                heroTeacherEmail.setText(R.string.teacher_email_not_found);
                heroTeacherEmail.setTextColor(UIHelper.getThemeColor(this, R.attr.text_muted));
            }
            if (buttonCopyTeacherEmail != null) buttonCopyTeacherEmail.setVisibility(View.GONE);
        }
    }

    private void copyTeacherEmailToClipboard() {
        if (loadedTeacherEmail != null && !loadedTeacherEmail.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Teacher email", loadedTeacherEmail);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Logger.d(TAG, "Copied teacher email to clipboard");
                Toast.makeText(this, getString(R.string.teacher_email_copied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleTeacherEmailClick() {
        if (loadedTeacherEmail != null && !loadedTeacherEmail.isEmpty()) {
            try {
                Logger.d(TAG, "Opening mailto intent for teacher email");
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:" + loadedTeacherEmail));
                startActivity(emailIntent);
            } catch (ActivityNotFoundException e) {
                copyTeacherEmailToClipboard();
            }
        } else if (teacherEmailNetworkError && currentTeacherName != null) {
            Logger.i(TAG, "Retrying teacher email lookup on user tap");
            setupTeacherEmail(currentTeacherName);
        }
    }

    private void setPuwButtonLoading(boolean loading) {
        if (buttonOpenCourse != null) {
            buttonOpenCourse.setEnabled(!loading);
        }
        if (contentOpenCourse != null) {
            contentOpenCourse.animate().cancel();
            if (loading) {
                contentOpenCourse.animate().alpha(0f).setDuration(120)
                        .withEndAction(() -> contentOpenCourse.setVisibility(View.INVISIBLE)).start();
            } else {
                contentOpenCourse.setVisibility(View.VISIBLE);
                contentOpenCourse.animate().alpha(1f).setDuration(150).start();
            }
        }
        if (progressOpenCourse != null) {
            progressOpenCourse.animate().cancel();
            if (loading) {
                progressOpenCourse.setAlpha(0f);
                progressOpenCourse.setVisibility(View.VISIBLE);
                progressOpenCourse.animate().alpha(1f).setDuration(140).start();
            } else {
                progressOpenCourse.animate().alpha(0f).setDuration(140)
                        .withEndAction(() -> progressOpenCourse.setVisibility(View.GONE)).start();
            }
        }
    }

    private void handleOpenPuwCourse() {
        if (buttonOpenCourse != null && !buttonOpenCourse.isEnabled()) {
            return;
        }
        Logger.i(TAG + ".handleOpenPuwCourse", "User tapped PUW course button");
        Toast.makeText(this, getString(R.string.subject_opening_puw_course), Toast.LENGTH_SHORT).show();
        setPuwButtonLoading(true);
        Context appContext = getApplicationContext();

        AppExecutors.runIo(() -> {
            String foundCourseUrl = null;
            try {
                OkHttpClient client = PUW.globalLogin(appContext);
                if (client != null) {
                    foundCourseUrl = StudyPlanScraper.findCourseUrlCached(appContext, client, subjectName, rawLessonType);
                }
            } catch (Exception e) {
                Logger.e(TAG + ".handleOpenPuwCourse", "Error resolving course URL: " + Logger.formatException(e));
            }

            final String resolvedCourseUrl = foundCourseUrl;
            String targetUrl = MoodleAutologinManager.sanitizeTargetUrl(resolvedCourseUrl);
            MoodleAutologinManager.ResolutionResult resolution =
                    MoodleAutologinManager.resolveAutologinUriSync(appContext, targetUrl);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setPuwButtonLoading(false);
                if (resolvedCourseUrl == null) {
                    Toast.makeText(SubjectDetailsActivity.this, getString(R.string.subject_course_not_found), Toast.LENGTH_SHORT).show();
                }
                MoodleAutologinManager.launchExternalBrowser(SubjectDetailsActivity.this, resolution);
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (autoSaveRunnable != null) {
            notesHandler.removeCallbacks(autoSaveRunnable);
            autoSaveRunnable = null;
        }
        if (editSubjectNotes != null && editSubjectNotes.getText() != null) {
            persistNotes(editSubjectNotes.getText().toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        notesHandler.removeCallbacksAndMessages(null);
    }
}
