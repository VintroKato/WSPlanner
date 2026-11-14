package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

// pager adapter for horizontal day swiping across a 6 month window before and after today
public class DayPagerAdapter extends RecyclerView.Adapter<DayPagerAdapter.DayPageViewHolder> {
    private static final String TAG = "DayPagerAdapter";
    private static final int MONTHS_RANGE = 6;

    private final Context context;
    private final ScheduleRepository scheduleRepository;
    private final Set<DayPageViewHolder> boundHolders = new HashSet<>();
    private final RecyclerView.RecycledViewPool sharedViewPool = new RecyclerView.RecycledViewPool();

    private LocalDate anchorDate;
    private LocalDate minDate;
    private LocalDate maxDate;
    private int totalDays;

    private boolean noInternetState = false;
    private int pendingAnimatePosition = RecyclerView.NO_POSITION;

    public DayPagerAdapter(Context context, ScheduleRepository scheduleRepository) {
        this.context = context;
        this.scheduleRepository = scheduleRepository;
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_LESSON, 15);
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_BREAK_GAP, 10);
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_EMPTY, 5);
        updateDateRange(LocalDate.now());
    }

    // recalculate 6 month date range around the given anchor date
    public final void updateDateRange(LocalDate newAnchorDate) {
        this.anchorDate = newAnchorDate;
        this.minDate = newAnchorDate.minusMonths(MONTHS_RANGE);
        this.maxDate = newAnchorDate.plusMonths(MONTHS_RANGE);
        this.totalDays = (int) ChronoUnit.DAYS.between(minDate, maxDate) + 1;
        Logger.d(TAG, "Initialized day pager range: " + minDate + " to " + maxDate + " (" + totalDays + " days)");
    }

    // check if anchor date changed across midnight and update range if needed
    public boolean updateAnchorDateIfNeeded(LocalDate today) {
        if (!today.isEqual(anchorDate)) {
            Logger.i(TAG, "Anchor date changed from " + anchorDate + " to " + today + ", updating pager range");
            updateDateRange(today);
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    public LocalDate getMinDate() {
        return minDate;
    }

    public LocalDate getMaxDate() {
        return maxDate;
    }

    public LocalDate getDateForPosition(int position) {
        int clamped = Math.max(0, Math.min(position, totalDays - 1));
        return minDate.plusDays(clamped);
    }

    public int getPositionForDate(LocalDate date) {
        if (date == null) {
            return getPositionForDate(anchorDate);
        }
        long diff = ChronoUnit.DAYS.between(minDate, date);
        return (int) Math.max(0, Math.min(diff, totalDays - 1));
    }

    @NonNull
    @Override
    public DayPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_day_page, parent, false);
        return new DayPageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayPageViewHolder holder, int position) {
        boundHolders.add(holder);
        LocalDate date = getDateForPosition(position);
        boolean shouldAnimate = (position == pendingAnimatePosition);
        if (shouldAnimate) {
            pendingAnimatePosition = RecyclerView.NO_POSITION;
        }
        holder.bind(date, shouldAnimate);
    }

    @Override
    public void onViewRecycled(@NonNull DayPageViewHolder holder) {
        super.onViewRecycled(holder);
        boundHolders.remove(holder);
    }

    @Override
    public int getItemCount() {
        return totalDays;
    }

    // request card entrance animation for the given page position on jump or refresh
    public void triggerCardAnimationForPosition(int position) {
        for (DayPageViewHolder holder : boundHolders) {
            if (holder.getBindingAdapterPosition() == position) {
                holder.bind(getDateForPosition(position), true);
                pendingAnimatePosition = RecyclerView.NO_POSITION;
                return;
            }
        }
        pendingAnimatePosition = position;
    }

    // refresh all bound pages once schedule is loaded in repository memory
    public void onScheduleLoaded(int animatePosition) {
        this.noInternetState = false;
        this.pendingAnimatePosition = animatePosition;
        Logger.d(TAG, "Refreshing bound day pages (count=" + boundHolders.size() + ", animatePosition=" + animatePosition + ")");
        for (DayPageViewHolder holder : boundHolders) {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                boolean animate = (pos == animatePosition);
                if (animate) {
                    pendingAnimatePosition = RecyclerView.NO_POSITION;
                }
                holder.bind(getDateForPosition(pos), animate);
            }
        }
    }

    // display offline error state on bound pages when schedule cannot be loaded
    public void showNoInternetState() {
        this.noInternetState = true;
        this.pendingAnimatePosition = RecyclerView.NO_POSITION;
        Logger.d(TAG, "Showing no internet state on bound day pages");
        for (DayPageViewHolder holder : boundHolders) {
            holder.scheduleAdapter.showNoInternetState();
        }
    }

    // clear all bound pages while reloading after configuration change
    public void clearAll() {
        this.noInternetState = false;
        this.pendingAnimatePosition = RecyclerView.NO_POSITION;
        for (DayPageViewHolder holder : boundHolders) {
            holder.scheduleAdapter.clear();
        }
    }

    // refresh a specific date in place for minute ticker updates without slide animation
    public void refreshDate(LocalDate date) {
        if (date == null || noInternetState || scheduleRepository == null || !scheduleRepository.isMemoryCacheLoaded()) {
            return;
        }
        for (DayPageViewHolder holder : boundHolders) {
            if (date.equals(holder.boundDate)) {
                DaySchedule daySchedule = scheduleRepository.getScheduleForDateFromMemory(date);
                holder.scheduleAdapter.submitDaySchedule(daySchedule);
            }
        }
    }

    class DayPageViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;
        final ScheduleAdapter scheduleAdapter;
        LocalDate boundDate;

        DayPageViewHolder(@NonNull View itemView) {
            super(itemView);
            recyclerView = itemView.findViewById(R.id.day_recycler_view);
            scheduleAdapter = new ScheduleAdapter(context);
            LinearLayoutManager layoutManager = new LinearLayoutManager(context);
            layoutManager.setInitialPrefetchItemCount(6);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setRecycledViewPool(sharedViewPool);
            recyclerView.setHasFixedSize(true);
            recyclerView.setAdapter(scheduleAdapter);
            recyclerView.setItemAnimator(null);
        }

        void bind(LocalDate date, boolean animateCards) {
            this.boundDate = date;
            if (noInternetState) {
                scheduleAdapter.showNoInternetState();
                return;
            }
            if (scheduleRepository != null && scheduleRepository.isMemoryCacheLoaded()) {
                DaySchedule daySchedule = scheduleRepository.getScheduleForDateFromMemory(date);
                if (animateCards) {
                    if (recyclerView.getLayoutAnimation() == null) {
                        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_slide_up);
                        recyclerView.setLayoutAnimation(animation);
                    }
                    recyclerView.scheduleLayoutAnimation();
                }
                scheduleAdapter.submitDaySchedule(daySchedule);
            } else {
                scheduleAdapter.clear();
            }
        }
    }
}
