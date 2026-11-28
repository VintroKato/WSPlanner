package com.vintro.wsplanner.ui.fragments;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.network.MoodleAutologinManager;
import com.vintro.wsplanner.ui.adapters.DayPagerAdapter;
import com.vintro.wsplanner.ui.adapters.ScheduleAdapter;
import com.vintro.wsplanner.ui.adapters.WeekStripAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;
import com.vintro.wsplanner.utils.ScheduleFileOpener;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ScheduleFragment extends Fragment {
    private static final String TAG = "ScheduleFragment";
    private static final String ARG_SELECTED_DATE = "arg_selected_date";
    private static final String STATE_SELECTED_DATE = "state_selected_date";
    private static final int DAYS_RANGE_HALF = 180;
    private static final String DATE_PICKER_TAG = "schedule_material_date_picker";

    private View buttonOpenFile;
    private ImageView iconOpenFile;
    private ProgressBar progressOpenFile;
    private View buttonCalendarPicker;
    private TextView monthHeaderText;
    private View buttonOpenPuw;
    private ImageView iconOpenPuw;
    private ProgressBar progressOpenPuw;
    private ViewPager2 weekStripPager;
    private View pillSyncStatus;
    private ImageView iconSyncRefresh;
    private TextView lastSyncText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ViewPager2 scheduleViewPager;
    private View fabBackToToday;
    private ImageView iconBackToToday;

    private WeekStripAdapter weekStripAdapter;
    private DayPagerAdapter dayPagerAdapter;
    private ScheduleRepository scheduleRepository;

    private LocalDate anchorToday = LocalDate.now();
    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalDate selectedDate = LocalDate.now();
    @Nullable
    private LocalDate extraPickedDate = null;

    private final List<LocalDate> visibleDates = new ArrayList<>();
    private Set<DayOfWeek> activeVisibleWeekdays = EnumSet.noneOf(DayOfWeek.class);
    private String activeConfigSignature;
    private long lastObservedDataVersion = -1L;
    private boolean isBackToTodayVisible = false;
    private boolean isSyncSpinning = false;
    private boolean isOpeningPuw = false;
    private boolean isRebuildingWeekStrip = false;
    private boolean pendingWeekdaysRebuild = false;
    private ObjectAnimator syncSpinAnimator;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;

    private final DateTimeFormatter syncTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter syncDateFormatter = DateTimeFormatter.ofPattern("dd.MM, HH:mm");

    public static ScheduleFragment newInstance(@Nullable LocalDate initialDate) {
        ScheduleFragment fragment = new ScheduleFragment();
        if (initialDate != null) {
            Bundle args = new Bundle();
            args.putString(ARG_SELECTED_DATE, initialDate.toString());
            fragment.setArguments(args);
        }
        return fragment;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String restored = null;
        if (savedInstanceState != null) {
            restored = savedInstanceState.getString(STATE_SELECTED_DATE);
        } else if (getArguments() != null) {
            restored = getArguments().getString(ARG_SELECTED_DATE);
        }
        if (restored != null) {
            try {
                selectedDate = LocalDate.parse(restored);
            } catch (Exception ignored) {
                selectedDate = LocalDate.now();
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Logger.d(TAG + ".onViewCreated", "ScheduleFragment created");
        Context context = requireContext();
        scheduleRepository = ScheduleRepository.getInstance(context);
        activeConfigSignature = scheduleRepository.buildConfigSignature();

        initViews(view);
        setupAdapters();
        setupListeners();

        rebuildVisibleDates(true, false);
        updateLastSyncUI();
        loadSchedule(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedDate != null) {
            outState.putString(STATE_SELECTED_DATE, selectedDate.toString());
        }
    }

    private void initViews(View view) {
        buttonOpenFile = view.findViewById(R.id.button_open_file);
        iconOpenFile = view.findViewById(R.id.icon_open_file);
        progressOpenFile = view.findViewById(R.id.progress_open_file);
        buttonCalendarPicker = view.findViewById(R.id.button_calendar_picker);
        monthHeaderText = view.findViewById(R.id.month_header_text);
        buttonOpenPuw = view.findViewById(R.id.button_open_puw);
        iconOpenPuw = view.findViewById(R.id.icon_open_puw);
        progressOpenPuw = view.findViewById(R.id.progress_open_puw);
        weekStripPager = view.findViewById(R.id.week_strip_pager);
        pillSyncStatus = view.findViewById(R.id.pill_sync_status);
        iconSyncRefresh = view.findViewById(R.id.icon_sync_refresh);
        lastSyncText = view.findViewById(R.id.last_sync_text);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        scheduleViewPager = view.findViewById(R.id.schedule_view_pager);
        fabBackToToday = view.findViewById(R.id.fab_back_to_today);
        iconBackToToday = view.findViewById(R.id.icon_back_to_today);

        TooltipCompat.setTooltipText(buttonOpenPuw, getString(R.string.schedule_open_puw_tooltip));
        TooltipCompat.setTooltipText(buttonOpenFile, getString(R.string.widget_action_open_excel));

        setupSwipeRefreshColors();
    }

    private void setupSwipeRefreshColors() {
        if (swipeRefreshLayout == null || !isAdded()) return;
        Context context = requireContext();
        int progressBgColor = UIHelper.getThemeColor(context, R.attr.card_bg);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(progressBgColor);
        swipeRefreshLayout.setColorSchemeColors(
                UIHelper.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary),
                UIHelper.getThemeColor(context, R.attr.next_lesson_border)
        );
    }

    private void setupAdapters() {
        Context context = requireContext();

        weekStripAdapter = new WeekStripAdapter(context, scheduleRepository, clickedDate -> {
            Logger.d(TAG + ".weekStripClick", "User selected date from weekly strip: " + clickedDate);
            navigateToDate(clickedDate);
        });
        weekStripPager.setAdapter(weekStripAdapter);
        weekStripPager.setOffscreenPageLimit(1);
        setupWeekStripPeek(context);

        weekStripPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (isRebuildingWeekStrip) {
                    return;
                }
                List<LocalDate> weekDates = weekStripAdapter.getWeekAt(position);
                if (weekDates == null || weekDates.isEmpty()) return;

                // ignore programmatic sync when selectedDate already belongs to this week
                if (selectedDate != null && weekDates.contains(selectedDate)) {
                    return;
                }

                // select today if this week contains today; otherwise select first visible day of the week
                LocalDate today = LocalDate.now();
                LocalDate targetDate = weekDates.contains(today) ? today : weekDates.get(0);
                if (!targetDate.isEqual(selectedDate)) {
                    Logger.d(TAG + ".weekStripSwipe", "Week page changed to pos=" + position + ", auto-selecting " + targetDate);
                    navigateToDateInternal(targetDate, true);
                }
            }
        });

        dayPagerAdapter = new DayPagerAdapter(context, scheduleRepository);
        dayPagerAdapter.setEmptyActionListener(new ScheduleAdapter.OnEmptyActionListener() {
            @Override
            public void onJumpToNextClassDay(LocalDate nextClassDate) {
                Logger.i(TAG, "User tapped jump to next class day: " + nextClassDate);
                navigateToDate(nextClassDate);
            }

            @Override
            public void onRetryLoad() {
                Logger.i(TAG, "User tapped retry on offline schedule screen");
                handleManualRefresh(false);
            }
        });
        scheduleViewPager.setAdapter(dayPagerAdapter);
        scheduleViewPager.setOffscreenPageLimit(1);

        scheduleViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                LocalDate pageDate = dayPagerAdapter.getDateForPosition(position);
                if (pageDate != null && !pageDate.isEqual(selectedDate)) {
                    selectedDate = pageDate;
                    Logger.d(TAG + ".onPageSelected", "Selected date changed by day pager to: " + selectedDate);
                    updateMonthHeader();
                    weekStripAdapter.setSelectedDate(selectedDate);
                    syncWeekPagerToDate(selectedDate, true);
                    updateBackToTodayPill(true);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setEnabled(state == ViewPager2.SCROLL_STATE_IDLE);
                }
            }
        });
    }

    // align active week cards strictly to 16dp screen margins while revealing adjacent week edges
    private void setupWeekStripPeek(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        // 13dp recycler padding + 3dp outer card margin = exact 16dp alignment with header and cards
        int pagerSidePaddingPx = Math.round(13f * density);
        int betweenWeeksGapPx = Math.round(2f * density);

        View child = weekStripPager.getChildAt(0);
        if (child instanceof RecyclerView) {
            RecyclerView innerRecycler = (RecyclerView) child;
            innerRecycler.setPadding(pagerSidePaddingPx, 0, pagerSidePaddingPx, 0);
            innerRecycler.setClipToPadding(false);
            innerRecycler.setClipChildren(false);
            innerRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
            RecyclerView.ItemAnimator itemAnimator = innerRecycler.getItemAnimator();
            if (itemAnimator instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
            }
            // keep page transformer alpha and margins accurate after dataset or visibility changes
            innerRecycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (weekStripPager != null && weekStripPager.getVisibility() == View.VISIBLE) {
                    weekStripPager.post(() -> {
                        if (weekStripPager != null && isAdded()) {
                            weekStripPager.requestTransform();
                        }
                    });
                }
            });
        }

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(betweenWeeksGapPx));
        transformer.addTransformer((page, position) -> {
            float absPos = Math.min(1f, Math.abs(position));
            page.setAlpha(1f - 0.58f * absPos);
        });
        weekStripPager.setPageTransformer(transformer);
    }

    private void setupListeners() {
        buttonOpenFile.setOnClickListener(v -> openScheduleFile());
        buttonCalendarPicker.setOnClickListener(v -> openCalendarPicker());
        buttonOpenPuw.setOnClickListener(v -> openPuwHome());

        pillSyncStatus.setOnClickListener(v -> {
            Logger.i(TAG, "User tapped sync status row to refresh schedule");
            handleManualRefresh(false);
        });

        fabBackToToday.setOnClickListener(v -> {
            Logger.i(TAG, "User tapped floating back to today pill");
            navigateToDate(LocalDate.now());
        });

        swipeRefreshLayout.setOnRefreshListener(() -> handleManualRefresh(true));
    }

    private void updateRangeBounds(LocalDate today) {
        this.anchorToday = today;
        this.minDate = today.minusDays(DAYS_RANGE_HALF);
        this.maxDate = today.plusDays(DAYS_RANGE_HALF);
    }

    // build unified filtered date list shared by both weekly strip and day ViewPager2
    private void rebuildVisibleDates(boolean jumpStripInstantly, boolean animateJump) {
        if (!isAdded()) return;
        LocalDate today = LocalDate.now();
        updateRangeBounds(today);

        Set<DayOfWeek> prefDays = PreferencesManager.getVisibleWeekdaysPref(requireContext());
        activeVisibleWeekdays = EnumSet.copyOf(prefDays);

        List<LocalDate> newVisibleDates = new ArrayList<>();
        LocalDate cursor = minDate;
        while (!cursor.isAfter(maxDate)) {
            boolean isToday = cursor.isEqual(today);
            boolean isAllowedWeekday = activeVisibleWeekdays.contains(cursor.getDayOfWeek());
            boolean hasLessons = scheduleRepository != null && scheduleRepository.hasLessonsOnDateFromMemory(cursor);
            boolean isExtraPicked = extraPickedDate != null && cursor.isEqual(extraPickedDate);

            if (isToday || isAllowedWeekday || hasLessons || isExtraPicked) {
                newVisibleDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        if (newVisibleDates.isEmpty()) {
            newVisibleDates.add(today);
        }

        boolean datesChanged = !newVisibleDates.equals(visibleDates);
        if (datesChanged) {
            visibleDates.clear();
            visibleDates.addAll(newVisibleDates);
        }

        // keep selectedDate valid inside the updated filtered date list
        int targetPos = findExactOrNearestPosition(selectedDate);
        selectedDate = visibleDates.get(targetPos);

        if (datesChanged) {
            dayPagerAdapter.submitDates(visibleDates);
        }
        isRebuildingWeekStrip = true;
        try {
            weekStripAdapter.submitDates(visibleDates, today, selectedDate);
        } finally {
            isRebuildingWeekStrip = false;
        }

        if (datesChanged || scheduleViewPager.getCurrentItem() != targetPos) {
            if (animateJump) {
                dayPagerAdapter.triggerCardAnimationForPosition(targetPos);
            }
            scheduleViewPager.setCurrentItem(targetPos, false);
        }

        syncWeekPagerToDate(selectedDate, !jumpStripInstantly);
        updateMonthHeader();
        updateBackToTodayPill(false);
    }

    private void syncWeekPagerToDate(LocalDate date, boolean smooth) {
        if (!isAdded() || weekStripPager == null || weekStripAdapter == null) return;
        int weekPos = weekStripAdapter.getWeekPositionForDate(date);
        if (weekPos == RecyclerView.NO_POSITION) return;
        if (weekStripPager.getCurrentItem() != weekPos) {
            weekStripPager.setCurrentItem(weekPos, smooth);
        } else if (!smooth) {
            View child = weekStripPager.getChildAt(0);
            if (child instanceof RecyclerView) {
                ((RecyclerView) child).scrollToPosition(weekPos);
            }
        }
        requestWeekStripTransform();
    }

    private void requestWeekStripTransform() {
        if (weekStripPager == null) return;
        weekStripPager.post(() -> {
            if (weekStripPager != null && isAdded()) {
                weekStripPager.requestTransform();
            }
        });
    }

    private int findExactOrNearestPosition(LocalDate target) {
        if (visibleDates.isEmpty()) return 0;
        if (target == null) target = LocalDate.now();
        for (int i = 0; i < visibleDates.size(); i++) {
            LocalDate d = visibleDates.get(i);
            if (d.isEqual(target)) {
                return i;
            }
            if (d.isAfter(target)) {
                return i;
            }
        }
        return visibleDates.size() - 1;
    }

    public void onVisibleWeekdaysChanged() {
        if (!isAdded()) return;
        // defer ViewPager2 rebuild while fragment is hidden so pages never lay out with stale alpha
        if (isHidden() || getView() == null || !isResumed()) {
            Logger.d(TAG, "Deferred visible weekdays rebuild until ScheduleFragment becomes visible");
            pendingWeekdaysRebuild = true;
            return;
        }
        Logger.i(TAG, "Visible weekdays changed, rebuilding schedule date list");
        pendingWeekdaysRebuild = false;
        extraPickedDate = null;
        rebuildVisibleDates(true, false);
        if (dayPagerAdapter != null && scheduleViewPager != null) {
            dayPagerAdapter.onScheduleLoaded(RecyclerView.NO_POSITION);
        }
    }

    public void onTabReselected() {
        if (!isAdded()) return;
        Logger.d(TAG, "Schedule tab reselected, returning to today and scrolling to top");
        navigateToDate(LocalDate.now());
        if (dayPagerAdapter != null && scheduleViewPager != null) {
            dayPagerAdapter.scrollToTop(scheduleViewPager.getCurrentItem());
        }
    }

    public void onActivatedFromWidget() {
        if (!isAdded()) return;
        Logger.d(TAG, "Schedule activated from widget");
        navigateToDate(LocalDate.now());
        if (dayPagerAdapter != null && scheduleViewPager != null) {
            dayPagerAdapter.scrollToTop(scheduleViewPager.getCurrentItem());
        }
    }

    private void navigateToDate(LocalDate targetDate) {
        navigateToDateInternal(targetDate, false);
    }

    private void navigateToDateInternal(LocalDate targetDate, boolean fromWeekSwipe) {
        if (dayPagerAdapter == null || scheduleViewPager == null || targetDate == null) return;

        // clamp to range bounds
        LocalDate bounded = targetDate;
        if (bounded.isBefore(minDate)) bounded = minDate;
        if (bounded.isAfter(maxDate)) bounded = maxDate;

        // if target date is currently hidden, temporarily include it in the shared visible dates list
        if (!visibleDates.contains(bounded)) {
            extraPickedDate = bounded;
            selectedDate = bounded;
            rebuildVisibleDates(false, true);
            return;
        }

        int currentPos = scheduleViewPager.getCurrentItem();
        int targetPos = dayPagerAdapter.getPositionForDate(bounded);
        LocalDate resolvedDate = dayPagerAdapter.getDateForPosition(targetPos);
        if (resolvedDate == null) return;

        selectedDate = resolvedDate;
        updateMonthHeader();
        weekStripAdapter.setSelectedDate(selectedDate);
        if (!fromWeekSwipe) {
            syncWeekPagerToDate(selectedDate, true);
        }
        updateBackToTodayPill(true);

        if (targetPos == currentPos) {
            return;
        }

        int distance = Math.abs(targetPos - currentPos);
        if (distance == 1) {
            Logger.d(TAG + ".navigateToDate", "Smooth scrolling to adjacent date: " + resolvedDate);
            scheduleViewPager.setCurrentItem(targetPos, true);
        } else {
            Logger.d(TAG + ".navigateToDate", "Jumping to date: " + resolvedDate + " with card entrance animation");
            dayPagerAdapter.triggerCardAnimationForPosition(targetPos);
            scheduleViewPager.setCurrentItem(targetPos, false);
        }
    }

    private void updateMonthHeader() {
        if (!isAdded() || monthHeaderText == null || selectedDate == null) return;
        Locale locale = Locale.getDefault();
        DateTimeFormatter monthYearFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale);
        String formatted = selectedDate.format(monthYearFormatter);
        if (formatted != null && !formatted.isEmpty()) {
            formatted = formatted.substring(0, 1).toUpperCase(locale) + formatted.substring(1);
        }
        monthHeaderText.setText(formatted);
    }

    private void updateBackToTodayPill(boolean animate) {
        if (!isAdded() || fabBackToToday == null || selectedDate == null) return;
        LocalDate today = LocalDate.now();
        boolean shouldShow = !selectedDate.isEqual(today);

        // keep arrow fixed on the left and rotate smoothly 0deg (left) <-> 180deg (right)
        if (shouldShow && iconBackToToday != null) {
            float targetRotation = selectedDate.isAfter(today) ? 0f : 180f;
            if (animate && isBackToTodayVisible) {
                iconBackToToday.animate().rotation(targetRotation).setDuration(200).start();
            } else {
                iconBackToToday.animate().cancel();
                iconBackToToday.setRotation(targetRotation);
            }
        }

        if (shouldShow == isBackToTodayVisible && animate) {
            return;
        }
        isBackToTodayVisible = shouldShow;
        fabBackToToday.animate().cancel();

        float hiddenTranslationY = 24f * getResources().getDisplayMetrics().density;
        if (shouldShow) {
            if (!animate) {
                fabBackToToday.setVisibility(View.VISIBLE);
                fabBackToToday.setAlpha(1f);
                fabBackToToday.setScaleX(1f);
                fabBackToToday.setScaleY(1f);
                fabBackToToday.setTranslationY(0f);
            } else {
                if (fabBackToToday.getVisibility() != View.VISIBLE) {
                    fabBackToToday.setAlpha(0f);
                    fabBackToToday.setScaleX(0.92f);
                    fabBackToToday.setScaleY(0.92f);
                    fabBackToToday.setTranslationY(hiddenTranslationY);
                    fabBackToToday.setVisibility(View.VISIBLE);
                }
                fabBackToToday.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .start();
            }
        } else {
            if (!animate) {
                fabBackToToday.setVisibility(View.GONE);
                fabBackToToday.setAlpha(0f);
                fabBackToToday.setTranslationY(hiddenTranslationY);
            } else {
                fabBackToToday.animate()
                        .alpha(0f)
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .translationY(hiddenTranslationY)
                        .setDuration(180)
                        .withEndAction(() -> {
                            if (!isBackToTodayVisible && fabBackToToday != null) {
                                fabBackToToday.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
    }

    private void setSyncSpinning(boolean spinning) {
        if (iconSyncRefresh == null) return;
        if (spinning == isSyncSpinning) return;
        isSyncSpinning = spinning;

        if (spinning) {
            if (syncSpinAnimator == null) {
                syncSpinAnimator = ObjectAnimator.ofFloat(iconSyncRefresh, View.ROTATION, 0f, 360f);
                syncSpinAnimator.setDuration(800);
                syncSpinAnimator.setRepeatCount(ValueAnimator.INFINITE);
                syncSpinAnimator.setInterpolator(new LinearInterpolator());
            }
            syncSpinAnimator.start();
        } else {
            if (syncSpinAnimator != null && syncSpinAnimator.isRunning()) {
                syncSpinAnimator.cancel();
            }
            iconSyncRefresh.animate().rotation(0f).setDuration(180).start();
        }
    }

    private void updateLastSyncUI() {
        if (!isAdded() || pillSyncStatus == null || lastSyncText == null || scheduleRepository == null) return;
        LocalDateTime syncTime = scheduleRepository.getLastSyncTime();
        if (syncTime == null) {
            pillSyncStatus.setVisibility(View.GONE);
            return;
        }

        pillSyncStatus.setVisibility(View.VISIBLE);
        LocalDate syncDate = syncTime.toLocalDate();
        LocalDate today = LocalDate.now();
        String timeStr = syncTime.format(syncTimeFormatter);

        String text;
        if (syncDate.isEqual(today)) {
            text = getString(R.string.schedule_updated_prefix, getString(R.string.schedule_updated_today, timeStr));
        } else if (syncDate.isEqual(today.minusDays(1))) {
            text = getString(R.string.schedule_updated_prefix, getString(R.string.schedule_updated_yesterday, timeStr));
        } else {
            text = getString(R.string.schedule_updated_prefix, syncTime.format(syncDateFormatter));
        }
        lastSyncText.setText(text);
    }

    public void navigateToTargetDate(@Nullable LocalDate targetDate) {
        if (!isAdded() || targetDate == null) return;
        Logger.d(TAG + ".navigateToTargetDate", "Navigating schedule to requested date: " + targetDate);
        navigateToDate(targetDate);
        if (dayPagerAdapter != null && scheduleViewPager != null) {
            dayPagerAdapter.scrollToTop(scheduleViewPager.getCurrentItem());
        }
    }

    private void loadSchedule(boolean forceRefresh) {
        if (!isAdded() || scheduleRepository == null || !scheduleRepository.isConfigurationComplete()) {
            Logger.d(TAG + ".loadSchedule", "Student configuration incomplete, skipping schedule load");
            return;
        }
        Logger.d(TAG + ".loadSchedule", "Loading schedule for date: " + selectedDate + " (forceRefresh=" + forceRefresh + ")");
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        setSyncSpinning(true);

        scheduleRepository.getScheduleForDate(selectedDate, forceRefresh, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
            @Override
            public void onSuccess(DaySchedule result) {
                Logger.i(TAG + ".loadSchedule", "Schedule loaded successfully. Day items count: " + (result != null ? result.getLessons().size() : 0));
                Activity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    setSyncSpinning(false);
                    lastObservedDataVersion = scheduleRepository.getDataVersion();
                    rebuildVisibleDates(true, false);
                    if (dayPagerAdapter != null && scheduleViewPager != null) {
                        dayPagerAdapter.onScheduleLoaded(scheduleViewPager.getCurrentItem());
                    }
                    updateLastSyncUI();
                    if (forceRefresh && result != null && result.isFromCacheFallback()) {
                        Logger.i(TAG + ".loadSchedule", "Displaying schedule from offline cache fallback");
                        Toast.makeText(requireContext(), R.string.schedule_offline_cached_toast, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e(TAG + ".loadSchedule", "Failed to load schedule: " + Logger.formatException(e), e);
                Activity activity = getActivity();
                if (!isAdded() || activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    setSyncSpinning(false);
                    updateLastSyncUI();
                    if (dayPagerAdapter != null) {
                        dayPagerAdapter.showNoInternetState();
                    }
                });
            }
        });
    }

    private void handleManualRefresh(boolean fromSwipe) {
        if (!isAdded() || scheduleRepository == null) return;
        long cooldownLeft = scheduleRepository.getCooldownRemainingSeconds();
        if (cooldownLeft > 0) {
            Logger.d(TAG + ".handleManualRefresh", "Refresh blocked by cooldown: " + cooldownLeft + " seconds remaining");
            if (fromSwipe && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            Toast.makeText(requireContext(), getString(R.string.schedule_cooldown_message, cooldownLeft), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            Logger.w(TAG + ".handleManualRefresh", "Device is offline during manual refresh, attempting local cache fallback");
        }
        Logger.i(TAG + ".handleManualRefresh", "Triggering manual force-refresh (fromSwipe=" + fromSwipe + ")");
        loadSchedule(true);
    }

    // crossfade header button icon into compact spinner cleanly without overlapping
    private void setHeaderButtonLoading(View button, ImageView icon, ProgressBar spinner, boolean loading) {
        if (button != null) {
            button.setEnabled(!loading);
        }
        if (icon != null) {
            icon.animate().cancel();
            if (loading) {
                icon.animate().alpha(0f).setDuration(120).withEndAction(() -> icon.setVisibility(View.INVISIBLE)).start();
            } else {
                icon.setVisibility(View.VISIBLE);
                icon.animate().alpha(1f).setDuration(150).start();
            }
        }
        if (spinner != null) {
            spinner.animate().cancel();
            if (loading) {
                spinner.setAlpha(0f);
                spinner.setVisibility(View.VISIBLE);
                spinner.animate().alpha(1f).setDuration(140).start();
            } else {
                spinner.animate().alpha(0f).setDuration(140).withEndAction(() -> spinner.setVisibility(View.GONE)).start();
            }
        }
    }

    private void openScheduleFile() {
        if (!isAdded() || (buttonOpenFile != null && !buttonOpenFile.isEnabled())) return;
        Logger.d(TAG + ".openScheduleFile", "User requested to open schedule Excel file");
        Toast.makeText(requireContext(), getString(R.string.schedule_opening_file), Toast.LENGTH_SHORT).show();
        setHeaderButtonLoading(buttonOpenFile, iconOpenFile, progressOpenFile, true);

        ScheduleFileOpener.openExcelSchedule(requireContext(), () -> {
            if (!isAdded()) return;
            setHeaderButtonLoading(buttonOpenFile, iconOpenFile, progressOpenFile, false);
        });
    }

    private void openPuwHome() {
        if (!isAdded() || isOpeningPuw) return;
        isOpeningPuw = true;
        Logger.i(TAG + ".openPuwHome", "User tapped WSPA PUW header button on Schedule");
        Toast.makeText(requireContext(), getString(R.string.schedule_opening_puw), Toast.LENGTH_SHORT).show();
        setHeaderButtonLoading(buttonOpenPuw, iconOpenPuw, progressOpenPuw, true);

        MoodleAutologinManager.openUrlWithAutologin(this, MoodleAutologinManager.DEFAULT_HOME_URL, () -> {
            isOpeningPuw = false;
            if (!isAdded()) return;
            setHeaderButtonLoading(buttonOpenPuw, iconOpenPuw, progressOpenPuw, false);
        });
    }

    private void openCalendarPicker() {
        if (!isAdded() || getParentFragmentManager().findFragmentByTag(DATE_PICKER_TAG) != null) {
            return;
        }
        Logger.d(TAG + ".openCalendarPicker", "Opening MaterialDatePicker for date: " + selectedDate);
        long minUtc = minDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long maxUtc = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long selectedUtc = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        CalendarConstraints.DateValidator validator = CompositeDateValidator.allOf(Arrays.asList(
                DateValidatorPointForward.from(minUtc),
                DateValidatorPointBackward.before(maxUtc)
        ));

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(minUtc)
                .setEnd(maxUtc)
                .setOpenAt(selectedUtc)
                .setValidator(validator)
                .build();

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTheme(R.style.ThemeOverlay_WSPlanner_DatePicker)
                .setSelection(selectedUtc)
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || !isAdded()) return;
            LocalDate pickedDate = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            Logger.i(TAG + ".openCalendarPicker", "User picked date from calendar: " + pickedDate);
            navigateToDate(pickedDate);
        });

        picker.show(getParentFragmentManager(), DATE_PICKER_TAG);
    }

    private void startMinuteTicker() {
        stopMinuteTicker();

        long nowMillis = System.currentTimeMillis();
        long delayToNextMinute = 60000 - (nowMillis % 60000);

        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                onMinuteTick();
                tickerHandler.postDelayed(this, 60000);
            }
        };

        tickerHandler.postDelayed(tickerRunnable, delayToNextMinute);
    }

    private void stopMinuteTicker() {
        if (tickerRunnable != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
            tickerRunnable = null;
        }
    }

    private void onMinuteTick() {
        if (!isAdded() || isHidden() || dayPagerAdapter == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (!today.isEqual(anchorToday)) {
            Logger.i(TAG + ".onMinuteTick", "Midnight rollover detected during active session, syncing schedule state");
            syncStateIfActive();
            return;
        }
        if (scheduleRepository != null && scheduleRepository.isMemoryCacheLoaded()) {
            DaySchedule todaySchedule = scheduleRepository.getScheduleForDateFromMemory(today);
            if (todaySchedule == null || todaySchedule.isEmpty()) {
                return;
            }
            // skip redundant rebinds after all lessons for today finished more than two minutes ago
            LocalTime cutoff = LocalTime.now().minusMinutes(2);
            boolean hasActiveOrUpcoming = false;
            for (Lesson lesson : todaySchedule.getLessons()) {
                if (lesson.getEndTime() == null || lesson.getEndTime().isAfter(cutoff)) {
                    hasActiveOrUpcoming = true;
                    break;
                }
            }
            if (!hasActiveOrUpcoming) {
                return;
            }
        }
        dayPagerAdapter.refreshDate(today);
    }

    private void syncStateIfActive() {
        if (!isAdded() || scheduleRepository == null) return;

        LocalDate today = LocalDate.now();
        boolean midnightChanged = !today.isEqual(anchorToday);
        Set<DayOfWeek> prefDays = PreferencesManager.getVisibleWeekdaysPref(requireContext());
        boolean weekdaysChanged = pendingWeekdaysRebuild || !prefDays.equals(activeVisibleWeekdays);
        long currentDataVersion = scheduleRepository.getDataVersion();
        boolean dataVersionChanged = lastObservedDataVersion != -1L && currentDataVersion != lastObservedDataVersion;

        if (midnightChanged && selectedDate.isBefore(today)) {
            selectedDate = today;
        }

        if (midnightChanged || weekdaysChanged || dataVersionChanged) {
            Logger.i(TAG + ".syncStateIfActive", "Rebuilding visible dates (midnightChanged=" + midnightChanged
                    + ", weekdaysChanged=" + weekdaysChanged + ", dataVersionChanged=" + dataVersionChanged + ")");
            if (weekdaysChanged) {
                pendingWeekdaysRebuild = false;
                extraPickedDate = null;
            }
            lastObservedDataVersion = currentDataVersion;
            rebuildVisibleDates(true, false);
            if ((weekdaysChanged || dataVersionChanged) && dayPagerAdapter != null && scheduleViewPager != null) {
                dayPagerAdapter.onScheduleLoaded(RecyclerView.NO_POSITION);
            }
        } else {
            requestWeekStripTransform();
        }

        updateMonthHeader();
        updateBackToTodayPill(false);
        updateLastSyncUI();
        setupSwipeRefreshColors();

        String currentSig = scheduleRepository.buildConfigSignature();
        boolean configChanged = currentSig != null && !currentSig.equals(activeConfigSignature);
        if (configChanged) {
            Logger.i(TAG + ".syncStateIfActive", "Student configuration changed, reloading schedule");
            activeConfigSignature = currentSig;
            extraPickedDate = null;
            if (dayPagerAdapter != null) {
                dayPagerAdapter.clearAll();
            }
            loadSchedule(false);
        } else if (scheduleRepository.isMemoryCacheLoaded()) {
            if (dayPagerAdapter != null) {
                // refresh today and selected date so status badges and note indicators stay current
                dayPagerAdapter.refreshDate(today);
                if (selectedDate != null && !selectedDate.isEqual(today)) {
                    dayPagerAdapter.refreshDate(selectedDate);
                }
            }
        } else {
            loadSchedule(false);
        }

        checkScheduleFreshnessAndAutoUpdate();

        if ((midnightChanged || configChanged)
                && scheduleRepository.isConfigurationComplete()
                && WidgetUpdateManager.hasActiveWidgets(requireContext())) {
            WidgetUpdateManager.updateAllWidgets(requireContext());
            WidgetUpdateManager.scheduleNextAlarm(requireContext());
        }
    }

    private void checkScheduleFreshnessAndAutoUpdate() {
        if (!isAdded() || scheduleRepository == null || !scheduleRepository.isConfigurationComplete()) {
            return;
        }
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            return;
        }

        if (scheduleRepository.isScheduleOutdated()
                && scheduleRepository.getCooldownRemainingSeconds() == 0
                && NetworkUtils.isNetworkAvailable(requireContext())) {
            Logger.i(TAG, "Schedule is outdated (>= 6 hours old). Triggering auto-update.");
            loadSchedule(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            syncStateIfActive();
            startMinuteTicker();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            stopMinuteTicker();
        } else if (isResumed()) {
            syncStateIfActive();
            startMinuteTicker();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMinuteTicker();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopMinuteTicker();
        setSyncSpinning(false);
        if (syncSpinAnimator != null) {
            syncSpinAnimator.cancel();
            syncSpinAnimator = null;
        }
        tickerHandler.removeCallbacksAndMessages(null);
    }
}
