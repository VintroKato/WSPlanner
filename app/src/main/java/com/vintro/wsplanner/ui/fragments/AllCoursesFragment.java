package com.vintro.wsplanner.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.network.MoodleAutologinManager;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.ui.adapters.AllCoursesAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.OkHttpClient;

public class AllCoursesFragment extends Fragment {
    private static final String TAG = "AllCoursesFragment";
    private static final String KEY_SEARCH_QUERY = "state_search_query";
    private static final String KEY_LOCATIONS = "state_filter_locations";
    private static final String KEY_TYPES = "state_filter_types";
    private static final String KEY_STATUSES = "state_filter_statuses";
    private static final String KEY_FILTERS_EXPANDED = "state_filters_expanded";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final long DEBOUNCE_DELAY_MS = 250;

    private ScheduleRepository scheduleRepository;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private ViewGroup rootContainer;
    private TextView textCoursesCount;
    private EditText editSearch;
    private ImageButton buttonClearSearch;
    private View buttonToggleFilters;
    private ImageView iconFilter;
    private TextView badgeFilterCount;
    private View cardFiltersPanel;
    private TextView buttonResetFilters;
    private View sectionFilterLocation;
    private ChipGroup chipGroupLocation;
    private View sectionFilterType;
    private ChipGroup chipGroupType;
    private ChipGroup chipGroupStatus;
    private SwipeRefreshLayout swipeRefreshCourses;
    private RecyclerView recyclerAllCourses;
    private ProgressBar progressLoading;
    private LinearLayout layoutEmptyState;
    private MaterialButton buttonResetAllFilters;

    private AllCoursesAdapter adapter;
    private final List<SubjectDetails> allCourses = new ArrayList<>();
    private String studentMajor = "";
    private String activeConfigSignature;
    private long lastObservedDataVersion = -1L;
    private String initialSearchQuery = "";

    private final Set<LocationType> selectedLocations = EnumSet.noneOf(LocationType.class);
    private final Set<LessonType> selectedTypes = EnumSet.noneOf(LessonType.class);
    private final Set<String> selectedStatuses = new HashSet<>();
    private boolean filtersExpanded = false;
    private boolean isOpeningStrefa = false;

    public static AllCoursesFragment newInstance(@Nullable Bundle stateBundle) {
        AllCoursesFragment fragment = new AllCoursesFragment();
        if (stateBundle != null && !stateBundle.isEmpty()) {
            fragment.setArguments(new Bundle(stateBundle));
        }
        return fragment;
    }

    public Bundle exportFilterState() {
        Bundle state = new Bundle();
        state.putString(KEY_SEARCH_QUERY, getSearchQuery());
        state.putBoolean(KEY_FILTERS_EXPANDED, filtersExpanded);

        ArrayList<String> locNames = new ArrayList<>();
        for (LocationType loc : selectedLocations) {
            locNames.add(loc.name());
        }
        state.putStringArrayList(KEY_LOCATIONS, locNames);

        ArrayList<String> typeNames = new ArrayList<>();
        for (LessonType type : selectedTypes) {
            typeNames.add(type.name());
        }
        state.putStringArrayList(KEY_TYPES, typeNames);

        state.putStringArrayList(KEY_STATUSES, new ArrayList<>(selectedStatuses));
        return state;
    }

    public String getSearchQuery() {
        if (editSearch != null && editSearch.getText() != null) {
            return editSearch.getText().toString();
        }
        return initialSearchQuery;
    }

    public void onTabReselected() {
        if (isAdded() && recyclerAllCourses != null) {
            Logger.d(TAG, "Courses tab reselected, scrolling list to top");
            recyclerAllCourses.smoothScrollToPosition(0);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle source = savedInstanceState != null ? savedInstanceState : getArguments();
        if (source != null) {
            initialSearchQuery = source.getString(KEY_SEARCH_QUERY, "");
            filtersExpanded = source.getBoolean(KEY_FILTERS_EXPANDED, false);

            ArrayList<String> locNames = source.getStringArrayList(KEY_LOCATIONS);
            if (locNames != null) {
                for (String name : locNames) {
                    try {
                        selectedLocations.add(LocationType.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ArrayList<String> typeNames = source.getStringArrayList(KEY_TYPES);
            if (typeNames != null) {
                for (String name : typeNames) {
                    try {
                        selectedTypes.add(LessonType.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ArrayList<String> statuses = source.getStringArrayList(KEY_STATUSES);
            if (statuses != null) {
                selectedStatuses.addAll(statuses);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_courses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Logger.d(TAG + ".onViewCreated", "AllCoursesFragment created");
        Context context = requireContext();
        scheduleRepository = ScheduleRepository.getInstance(context);
        activeConfigSignature = scheduleRepository.buildConfigSignature();
        studentMajor = PreferencesManager.getGlobalMajorPref(context);
        if (studentMajor == null) studentMajor = "";

        initViews(view);
        setupSearch();
        setupStatusChips();
        if (initialSearchQuery != null && !initialSearchQuery.isEmpty()) {
            editSearch.setText(initialSearchQuery);
            editSearch.setSelection(initialSearchQuery.length());
        }
        cardFiltersPanel.setVisibility(filtersExpanded ? View.VISIBLE : View.GONE);
        updateFilterButtonUI();
        loadCourses(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(exportFilterState());
    }

    private void initViews(View view) {
        rootContainer = view.findViewById(R.id.all_courses_root);
        textCoursesCount = view.findViewById(R.id.text_courses_count);
        editSearch = view.findViewById(R.id.edit_search);
        buttonClearSearch = view.findViewById(R.id.button_clear_search);
        buttonToggleFilters = view.findViewById(R.id.button_toggle_filters);
        iconFilter = view.findViewById(R.id.icon_filter);
        badgeFilterCount = view.findViewById(R.id.badge_filter_count);
        cardFiltersPanel = view.findViewById(R.id.card_filters_panel);
        buttonResetFilters = view.findViewById(R.id.button_reset_filters);
        sectionFilterLocation = view.findViewById(R.id.section_filter_location);
        chipGroupLocation = view.findViewById(R.id.chip_group_location);
        sectionFilterType = view.findViewById(R.id.section_filter_type);
        chipGroupType = view.findViewById(R.id.chip_group_type);
        chipGroupStatus = view.findViewById(R.id.chip_group_status);
        swipeRefreshCourses = view.findViewById(R.id.swipe_refresh_courses);
        recyclerAllCourses = view.findViewById(R.id.recycler_all_courses);
        progressLoading = view.findViewById(R.id.progress_loading);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        buttonResetAllFilters = view.findViewById(R.id.button_reset_all_filters);

        adapter = new AllCoursesAdapter(requireContext(), this::openStudentZone);
        recyclerAllCourses.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerAllCourses.setAdapter(adapter);

        setupSwipeRefresh();

        buttonClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            applyFiltersAndSearch();
        });

        buttonToggleFilters.setOnClickListener(v -> toggleFilterPanel());
        buttonResetFilters.setOnClickListener(v -> resetAllFilters());
        buttonResetAllFilters.setOnClickListener(v -> resetAllFilters());
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshCourses == null || !isAdded()) return;
        Context context = requireContext();
        swipeRefreshCourses.setProgressBackgroundColorSchemeColor(UIHelper.getThemeColor(context, R.attr.card_bg));
        swipeRefreshCourses.setColorSchemeColors(
                UIHelper.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary),
                UIHelper.getThemeColor(context, R.attr.next_lesson_border)
        );
        swipeRefreshCourses.setOnRefreshListener(this::handleManualRefresh);
    }

    private void handleManualRefresh() {
        if (!isAdded() || scheduleRepository == null) return;
        long cooldownLeft = scheduleRepository.getCooldownRemainingSeconds();
        if (cooldownLeft > 0) {
            Logger.d(TAG + ".handleManualRefresh", "Courses refresh blocked by cooldown: " + cooldownLeft + "s");
            if (swipeRefreshCourses != null) {
                swipeRefreshCourses.setRefreshing(false);
            }
            Toast.makeText(requireContext(), getString(R.string.schedule_cooldown_message, cooldownLeft), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Logger.w(TAG + ".handleManualRefresh", "Device offline during courses pull-to-refresh");
        }
        Logger.i(TAG + ".handleManualRefresh", "User triggered pull-to-refresh on AllCoursesFragment");
        loadCourses(true);
    }

    private void toggleFilterPanel() {
        if (!isAdded()) return;
        filtersExpanded = !filtersExpanded;
        Logger.d(TAG + ".toggleFilterPanel", "Filter panel expanded=" + filtersExpanded);
        if (rootContainer != null) {
            Transition transition = new AutoTransition();
            transition.setDuration(180);
            TransitionManager.beginDelayedTransition(rootContainer, transition);
        }
        cardFiltersPanel.setVisibility(filtersExpanded ? View.VISIBLE : View.GONE);
    }

    private void resetAllFilters() {
        Logger.i(TAG + ".resetAllFilters", "Resetting all active course filters");
        selectedLocations.clear();
        selectedTypes.clear();
        selectedStatuses.clear();
        rebuildDynamicFilterChips();
        setupStatusChips();
        updateFilterButtonUI();
        applyFiltersAndSearch();
    }

    private int getActiveFilterCount() {
        return selectedLocations.size() + selectedTypes.size() + selectedStatuses.size();
    }

    private void updateFilterButtonUI() {
        if (!isAdded()) return;
        Context context = requireContext();
        int count = getActiveFilterCount();
        boolean hasFilters = count > 0;

        buttonToggleFilters.setBackgroundResource(hasFilters ? R.drawable.bg_filter_btn_active : R.drawable.bg_filter_btn);
        int iconTint = UIHelper.getThemeColor(context, hasFilters ? androidx.appcompat.R.attr.colorPrimary : R.attr.app_text);
        iconFilter.setImageTintList(ColorStateList.valueOf(iconTint));

        if (hasFilters) {
            boolean wasHidden = badgeFilterCount.getVisibility() != View.VISIBLE;
            badgeFilterCount.setText(String.valueOf(count));
            badgeFilterCount.setVisibility(View.VISIBLE);
            if (wasHidden) {
                badgeFilterCount.setScaleX(0.6f);
                badgeFilterCount.setScaleY(0.6f);
                badgeFilterCount.setAlpha(0f);
                badgeFilterCount.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
            }
            buttonResetFilters.setVisibility(View.VISIBLE);
        } else {
            badgeFilterCount.animate().cancel();
            badgeFilterCount.setVisibility(View.GONE);
            buttonResetFilters.setVisibility(View.GONE);
        }
    }

    private void rebuildDynamicFilterChips() {
        if (!isAdded()) return;
        Context context = requireContext();

        // collect available locations across all meetings of all courses
        Set<LocationType> availableLocations = EnumSet.noneOf(LocationType.class);
        Set<LessonType> availableTypes = EnumSet.noneOf(LessonType.class);

        for (SubjectDetails course : allCourses) {
            availableLocations.addAll(course.getAllLocationTypes());
            LessonType lt = course.getLessonType();
            if (lt != null && lt != LessonType.INNE) {
                availableTypes.add(lt);
            }
        }

        // prune selections no longer present after course reload
        selectedLocations.retainAll(availableLocations);
        selectedTypes.retainAll(availableTypes);

        // populate location chips in consistent order
        chipGroupLocation.removeAllViews();
        LocationType[] orderedLocations = new LocationType[]{
                LocationType.UCZELNIA,
                LocationType.ONLINE,
                LocationType.W_TERENIE
        };
        for (LocationType locType : orderedLocations) {
            if (!availableLocations.contains(locType)) continue;
            String label = getString(locType.getStringResId());
            boolean isChecked = selectedLocations.contains(locType);
            Chip chip = createFilterChip(context, label, isChecked);
            applyLocationChipStyle(context, chip, locType, isChecked);
            chip.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    selectedLocations.add(locType);
                } else {
                    selectedLocations.remove(locType);
                }
                applyLocationChipStyle(requireContext(), chip, locType, checked);
                updateFilterButtonUI();
                applyFiltersAndSearch();
            });
            chipGroupLocation.addView(chip);
        }
        sectionFilterLocation.setVisibility(chipGroupLocation.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        // populate localized lesson type chips sorted alphabetically
        chipGroupType.removeAllViews();
        List<LessonType> sortedTypes = new ArrayList<>(availableTypes);
        sortedTypes.sort(Comparator.comparing(t -> t.getLocalizedDisplayName(context), String.CASE_INSENSITIVE_ORDER));
        for (LessonType type : sortedTypes) {
            boolean isChecked = selectedTypes.contains(type);
            Chip chip = createFilterChip(context, type.getLocalizedDisplayName(context), isChecked);
            applyBlueChipStyle(context, chip, isChecked);
            chip.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    selectedTypes.add(type);
                } else {
                    selectedTypes.remove(type);
                }
                applyBlueChipStyle(requireContext(), chip, checked);
                updateFilterButtonUI();
                applyFiltersAndSearch();
            });
            chipGroupType.addView(chip);
        }
        sectionFilterType.setVisibility(chipGroupType.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void setupStatusChips() {
        if (!isAdded()) return;
        Context context = requireContext();
        chipGroupStatus.removeAllViews();

        addStatusChip(context, STATUS_ACTIVE, getString(R.string.courses_status_active));
        addStatusChip(context, STATUS_COMPLETED, getString(R.string.courses_status_completed));
    }

    private void addStatusChip(Context context, String statusKey, String label) {
        boolean isChecked = selectedStatuses.contains(statusKey);
        Chip chip = createFilterChip(context, label, isChecked);
        applyStatusChipStyle(context, chip, statusKey, isChecked);
        chip.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                selectedStatuses.add(statusKey);
            } else {
                selectedStatuses.remove(statusKey);
            }
            applyStatusChipStyle(requireContext(), chip, statusKey, checked);
            updateFilterButtonUI();
            applyFiltersAndSearch();
        });
        chipGroupStatus.addView(chip);
    }

    private Chip createFilterChip(Context context, String text, boolean checked) {
        float density = context.getResources().getDisplayMetrics().density;
        Chip chip = new Chip(context);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setCheckedIconVisible(false);
        chip.setChipIconVisible(false);
        chip.setCloseIconVisible(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(34f * density);
        chip.setChipCornerRadius(10f * density);
        chip.setChipStartPadding(10f * density);
        chip.setChipEndPadding(10f * density);
        chip.setTextStartPadding(2f * density);
        chip.setTextEndPadding(2f * density);
        chip.setTextSize(12.5f);
        chip.setElevation(0f);
        return chip;
    }

    private void applyLocationChipStyle(Context context, Chip chip, LocationType locType, boolean checked) {
        float density = context.getResources().getDisplayMetrics().density;
        if (!checked) {
            applyUncheckedChipStyle(context, chip, density);
            return;
        }
        int bgAttr;
        int borderAttr;
        int textAttr;
        if (locType == LocationType.ONLINE) {
            bgAttr = R.attr.badge_puw_bg;
            borderAttr = R.attr.badge_puw_border;
            textAttr = R.attr.badge_puw_text;
        } else if (locType == LocationType.W_TERENIE) {
            bgAttr = R.attr.badge_field_bg;
            borderAttr = R.attr.badge_field_border;
            textAttr = R.attr.badge_field_text;
        } else {
            bgAttr = R.attr.badge_wspa_bg;
            borderAttr = R.attr.badge_wspa_border;
            textAttr = R.attr.badge_wspa_text;
        }
        chip.setChipBackgroundColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, bgAttr)));
        chip.setChipStrokeColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, borderAttr)));
        chip.setChipStrokeWidth(1.0f * density);
        chip.setTextColor(UIHelper.getThemeColor(context, textAttr));
    }

    private void applyBlueChipStyle(Context context, Chip chip, boolean checked) {
        float density = context.getResources().getDisplayMetrics().density;
        if (!checked) {
            applyUncheckedChipStyle(context, chip, density);
            return;
        }
        chip.setChipBackgroundColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, R.attr.badge_type_bg)));
        chip.setChipStrokeColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, R.attr.badge_type_border)));
        chip.setChipStrokeWidth(1.0f * density);
        chip.setTextColor(UIHelper.getThemeColor(context, R.attr.badge_type_text));
    }

    private void applyStatusChipStyle(Context context, Chip chip, String statusKey, boolean checked) {
        float density = context.getResources().getDisplayMetrics().density;
        if (!checked) {
            applyUncheckedChipStyle(context, chip, density);
            return;
        }
        boolean isCompleted = STATUS_COMPLETED.equals(statusKey);
        int bgAttr = isCompleted ? R.attr.badge_next_bg : R.attr.badge_now_bg;
        int borderAttr = isCompleted ? R.attr.badge_next_border : R.attr.badge_now_border;
        int textAttr = isCompleted ? R.attr.badge_next_text : R.attr.badge_now_text;
        chip.setChipBackgroundColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, bgAttr)));
        chip.setChipStrokeColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, borderAttr)));
        chip.setChipStrokeWidth(1.0f * density);
        chip.setTextColor(UIHelper.getThemeColor(context, textAttr));
    }

    private void applyUncheckedChipStyle(Context context, Chip chip, float density) {
        chip.setChipBackgroundColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, R.attr.surface_recessed)));
        chip.setChipStrokeColor(ColorStateList.valueOf(UIHelper.getThemeColor(context, R.attr.card_border)));
        chip.setChipStrokeWidth(1.0f * density);
        chip.setTextColor(UIHelper.getThemeColor(context, R.attr.text_secondary));
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

                searchRunnable = () -> applyFiltersAndSearch();
                searchHandler.postDelayed(searchRunnable, DEBOUNCE_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCourses(boolean forceRefresh) {
        if (!isAdded() || scheduleRepository == null) return;
        if (!scheduleRepository.isConfigurationComplete()) {
            Logger.d(TAG + ".loadCourses", "Student configuration incomplete, showing empty courses");
            if (swipeRefreshCourses != null) {
                swipeRefreshCourses.setRefreshing(false);
            }
            progressLoading.setVisibility(View.GONE);
            allCourses.clear();
            rebuildDynamicFilterChips();
            updateFilterButtonUI();
            applyFiltersAndSearch();
            return;
        }

        Logger.d(TAG + ".loadCourses", "Loading all semester courses from repository (forceRefresh=" + forceRefresh + ")");
        if (!forceRefresh && allCourses.isEmpty()) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerAllCourses.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.GONE);
        }

        scheduleRepository.getAllCourses(forceRefresh, new ScheduleRepository.ScheduleCallback<List<SubjectDetails>>() {
            @Override
            public void onSuccess(List<SubjectDetails> result) {
                Logger.i(TAG + ".loadCourses", "Loaded " + (result != null ? result.size() : 0) + " semester courses");
                Activity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (swipeRefreshCourses != null) {
                        swipeRefreshCourses.setRefreshing(false);
                    }
                    progressLoading.setVisibility(View.GONE);
                    lastObservedDataVersion = scheduleRepository.getDataVersion();
                    allCourses.clear();
                    if (result != null) {
                        allCourses.addAll(result);
                    }
                    rebuildDynamicFilterChips();
                    updateFilterButtonUI();
                    applyFiltersAndSearch();
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e(TAG + ".loadCourses", "Error loading all courses: " + Logger.formatException(e));
                Activity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (swipeRefreshCourses != null) {
                        swipeRefreshCourses.setRefreshing(false);
                    }
                    progressLoading.setVisibility(View.GONE);
                    rebuildDynamicFilterChips();
                    updateFilterButtonUI();
                    applyFiltersAndSearch();
                });
            }
        });
    }

    private void checkConfigAndReloadIfNeeded() {
        if (!isAdded() || scheduleRepository == null) return;
        String currentSig = scheduleRepository.buildConfigSignature();
        long currentVersion = scheduleRepository.getDataVersion();
        boolean configChanged = currentSig != null && !currentSig.equals(activeConfigSignature);
        boolean dataChanged = currentVersion != lastObservedDataVersion;

        if (configChanged || dataChanged) {
            Logger.i(TAG + ".checkConfigAndReloadIfNeeded", "Courses refresh needed (configChanged=" + configChanged + ", dataChanged=" + dataChanged + ")");
            activeConfigSignature = currentSig;
            studentMajor = PreferencesManager.getGlobalMajorPref(requireContext());
            if (studentMajor == null) studentMajor = "";
            loadCourses(false);
        } else if (adapter != null) {
            // rebind visible cards so note indicator icons reflect edits made in SubjectDetailsActivity
            adapter.notifyDataSetChanged();
        }
    }

    private void applyFiltersAndSearch() {
        if (!isAdded() || adapter == null) return;
        Context context = requireContext();
        String rawQuery = getSearchQuery();
        String cleanQuery = rawQuery != null ? rawQuery.trim() : "";
        boolean hasQuery = !cleanQuery.isEmpty();
        boolean hasFilters = getActiveFilterCount() > 0;

        String[] tokens = hasQuery ? normalizePolishText(cleanQuery).split("\\s+") : null;

        boolean strefaMatches = false;
        if (!hasFilters) {
            if (!hasQuery) {
                strefaMatches = true;
            } else {
                String strefaSearchable = normalizePolishText("strefa studenta " + studentMajor);
                strefaMatches = matchesAllTokens(strefaSearchable, tokens);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<SubjectDetails> filtered = new ArrayList<>();
        for (SubjectDetails course : allCourses) {
            if (!matchesLocationFilter(course)) continue;
            if (!matchesTypeFilter(course)) continue;
            if (!matchesStatusFilter(course, now)) continue;

            if (hasQuery) {
                String localizedType = course.getLessonType() != null
                        ? course.getLessonType().getLocalizedDisplayName(context)
                        : "";
                String searchable = normalizePolishText(
                        course.getSubjectName() + " " +
                                (course.getRawLessonType() != null ? course.getRawLessonType() : "") + " " +
                                (course.getLessonType() != null ? course.getLessonType().getDisplayName() : "") + " " +
                                localizedType + " " +
                                course.getTeacherName()
                );
                if (!matchesAllTokens(searchable, tokens)) {
                    continue;
                }
            }
            filtered.add(course);
        }

        boolean hasResults = strefaMatches || !filtered.isEmpty();
        adapter.setData(filtered, strefaMatches, studentMajor);
        updateVisibility(hasResults, hasFilters);
        updateCount(filtered.size());
    }

    // course passes location filter if at least one of its scheduled meetings matches selected categories
    private boolean matchesLocationFilter(SubjectDetails course) {
        if (selectedLocations.isEmpty()) return true;
        Set<LocationType> courseLocations = course.getAllLocationTypes();
        for (LocationType selected : selectedLocations) {
            if (courseLocations.contains(selected)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTypeFilter(SubjectDetails course) {
        if (selectedTypes.isEmpty()) return true;
        return course.getLessonType() != null && selectedTypes.contains(course.getLessonType());
    }

    private boolean matchesStatusFilter(SubjectDetails course, LocalDateTime now) {
        if (selectedStatuses.isEmpty()) return true;
        int total = course.getTotalCount();
        int completed = course.getCompletedCount(now);
        boolean isCompleted = total > 0 && completed >= total;
        boolean matchActive = selectedStatuses.contains(STATUS_ACTIVE) && !isCompleted;
        boolean matchCompleted = selectedStatuses.contains(STATUS_COMPLETED) && isCompleted;
        return matchActive || matchCompleted;
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

    private void updateVisibility(boolean hasResults, boolean hasFilters) {
        if (hasResults) {
            recyclerAllCourses.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        } else {
            recyclerAllCourses.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (buttonResetAllFilters != null) {
                buttonResetAllFilters.setVisibility(hasFilters ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void updateCount(int count) {
        if (textCoursesCount != null) {
            if (count > 0) {
                textCoursesCount.setVisibility(View.VISIBLE);
                textCoursesCount.setText(String.valueOf(count));
            } else {
                textCoursesCount.setVisibility(View.GONE);
            }
        }
    }

    private void openStudentZone() {
        if (!isAdded() || isOpeningStrefa) return;
        isOpeningStrefa = true;
        Context appContext = requireContext().getApplicationContext();
        Logger.i(TAG + ".openStudentZone", "User tapped Student Zone card");
        Toast.makeText(requireContext(), R.string.all_courses_opening_strefa, Toast.LENGTH_SHORT).show();
        if (adapter != null) {
            adapter.setStrefaLoading(true);
        }

        AppExecutors.runIo(() -> {
            String url = null;
            try {
                OkHttpClient client = PUW.globalLogin(appContext);
                if (client != null) {
                    DegreeLevel degree = PreferencesManager.getGlobalDegreeLevelPref(appContext);
                    if (degree == DegreeLevel.ZERO_YEAR) {
                        Logger.i(TAG + ".openStudentZone", "Resolving student zone for Rok Zerowy participant");
                    }
                    url = StudyPlanScraper.getStudentZoneUrlCached(appContext, client, studentMajor, degree);
                }
            } catch (Exception e) {
                Logger.e(TAG + ".openStudentZone", "Error resolving student zone url: " + Logger.formatException(e));
            }

            String safeTargetUrl = MoodleAutologinManager.sanitizeTargetUrl(url);
            MoodleAutologinManager.ResolutionResult resolution =
                    MoodleAutologinManager.resolveAutologinUriSync(appContext, safeTargetUrl);

            Activity activity = getActivity();
            if (!isAdded() || activity == null) {
                isOpeningStrefa = false;
                return;
            }
            activity.runOnUiThread(() -> {
                isOpeningStrefa = false;
                if (!isAdded() || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (adapter != null) {
                    adapter.setStrefaLoading(false);
                }
                MoodleAutologinManager.launchExternalBrowser(activity, resolution);
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            checkConfigAndReloadIfNeeded();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isResumed()) {
            checkConfigAndReloadIfNeeded();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }
}
