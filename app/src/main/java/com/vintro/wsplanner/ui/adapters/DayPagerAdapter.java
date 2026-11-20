package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// pager adapter for horizontal day swiping backed by the filtered visible dates list
public class DayPagerAdapter extends RecyclerView.Adapter<DayPagerAdapter.DayPageViewHolder> {
    private static final String TAG = "DayPagerAdapter";

    private final Context context;
    private final ScheduleRepository scheduleRepository;
    private final Set<DayPageViewHolder> boundHolders = new HashSet<>();
    private final RecyclerView.RecycledViewPool sharedViewPool = new RecyclerView.RecycledViewPool();

    private final List<LocalDate> visibleDates = new ArrayList<>();
    @Nullable
    private ScheduleAdapter.OnEmptyActionListener emptyActionListener;

    private boolean noInternetState = false;
    private int pendingAnimatePosition = RecyclerView.NO_POSITION;

    public DayPagerAdapter(Context context, ScheduleRepository scheduleRepository) {
        this.context = context;
        this.scheduleRepository = scheduleRepository;
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_LESSON, 15);
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_BREAK_GAP, 10);
        sharedViewPool.setMaxRecycledViews(ScheduleItem.TYPE_EMPTY, 5);
    }

    public void setEmptyActionListener(@Nullable ScheduleAdapter.OnEmptyActionListener listener) {
        this.emptyActionListener = listener;
        for (DayPageViewHolder holder : boundHolders) {
            holder.scheduleAdapter.setEmptyActionListener(listener);
        }
    }

    public void submitDates(List<LocalDate> newDates) {
        visibleDates.clear();
        if (newDates != null) {
            visibleDates.addAll(newDates);
        }
        Logger.d(TAG, "Updated visible pager dates count=" + visibleDates.size());
        notifyDataSetChanged();
    }

    @Nullable
    public LocalDate getDateForPosition(int position) {
        if (visibleDates.isEmpty()) return null;
        int clamped = Math.max(0, Math.min(position, visibleDates.size() - 1));
        return visibleDates.get(clamped);
    }

    public int getPositionForDate(LocalDate date) {
        if (visibleDates.isEmpty()) return 0;
        if (date == null) return 0;

        for (int i = 0; i < visibleDates.size(); i++) {
            LocalDate item = visibleDates.get(i);
            if (item.isEqual(date)) {
                return i;
            }
            if (item.isAfter(date)) {
                return i;
            }
        }
        return visibleDates.size() - 1;
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
        return visibleDates.size();
    }

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

    public void showNoInternetState() {
        this.noInternetState = true;
        this.pendingAnimatePosition = RecyclerView.NO_POSITION;
        Logger.d(TAG, "Showing no internet state on bound day pages");
        for (DayPageViewHolder holder : boundHolders) {
            holder.scheduleAdapter.showNoInternetState();
        }
    }

    public void clearAll() {
        this.noInternetState = false;
        this.pendingAnimatePosition = RecyclerView.NO_POSITION;
        for (DayPageViewHolder holder : boundHolders) {
            holder.scheduleAdapter.clear();
        }
    }

    public void scrollToTop(int position) {
        for (DayPageViewHolder holder : boundHolders) {
            if (holder.getBindingAdapterPosition() == position && holder.recyclerView != null) {
                holder.recyclerView.smoothScrollToPosition(0);
                return;
            }
        }
    }

    public void refreshDate(LocalDate date) {
        if (date == null || noInternetState || scheduleRepository == null || !scheduleRepository.isMemoryCacheLoaded()) {
            return;
        }
        for (DayPageViewHolder holder : boundHolders) {
            if (date.equals(holder.boundDate)) {
                DaySchedule daySchedule = scheduleRepository.getScheduleForDateFromMemory(date);
                LocalDate nextClassDate = (daySchedule == null || daySchedule.isEmpty())
                        ? scheduleRepository.findNextClassDayAfterFromMemory(date)
                        : null;
                holder.scheduleAdapter.submitDaySchedule(daySchedule, nextClassDate);
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
            scheduleAdapter.setEmptyActionListener(emptyActionListener);
            LinearLayoutManager layoutManager = new LinearLayoutManager(context);
            layoutManager.setInitialPrefetchItemCount(6);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setRecycledViewPool(sharedViewPool);
            recyclerView.setHasFixedSize(true);
            recyclerView.setAdapter(scheduleAdapter);
            recyclerView.setItemAnimator(null);
        }

        void bind(@Nullable LocalDate date, boolean animateCards) {
            this.boundDate = date;
            scheduleAdapter.setEmptyActionListener(emptyActionListener);
            if (noInternetState) {
                scheduleAdapter.showNoInternetState();
                return;
            }
            if (date != null && scheduleRepository != null && scheduleRepository.isMemoryCacheLoaded()) {
                DaySchedule daySchedule = scheduleRepository.getScheduleForDateFromMemory(date);
                LocalDate nextClassDate = (daySchedule == null || daySchedule.isEmpty())
                        ? scheduleRepository.findNextClassDayAfterFromMemory(date)
                        : null;
                if (animateCards) {
                    if (recyclerView.getLayoutAnimation() == null) {
                        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_slide_up);
                        recyclerView.setLayoutAnimation(animation);
                    }
                    recyclerView.scheduleLayoutAnimation();
                }
                scheduleAdapter.submitDaySchedule(daySchedule, nextClassDate);
            } else {
                scheduleAdapter.clear();
            }
        }
    }
}
