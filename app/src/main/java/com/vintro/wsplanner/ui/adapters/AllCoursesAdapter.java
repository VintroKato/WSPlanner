package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.views.SegmentedProgressBar;
import com.vintro.wsplanner.ui.widgets.WidgetActionHelper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AllCoursesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_STREFA = 0;
    private static final int TYPE_COURSE = 1;

    public interface OnStrefaClickListener {
        void onStrefaClick();
    }

    private final Context context;
    private final List<SubjectDetails> courses = new ArrayList<>();
    private final int stripeActiveColor;
    private final int stripePastColor;
    private final int badgeTypeTextColor;
    private final OnStrefaClickListener onStrefaClickListener;

    private boolean showStrefa = true;
    private String majorName = "";
    private boolean isStrefaLoading = false;

    public AllCoursesAdapter(Context context, OnStrefaClickListener listener) {
        this.context = context;
        this.onStrefaClickListener = listener;
        this.stripeActiveColor = UIHelper.getThemeColor(context, R.attr.stripe_active);
        this.stripePastColor = UIHelper.getThemeColor(context, R.attr.stripe_past);
        this.badgeTypeTextColor = UIHelper.getThemeColor(context, R.attr.badge_type_text);
    }

    public void setStrefaLoading(boolean loading) {
        if (this.isStrefaLoading == loading) return;
        this.isStrefaLoading = loading;
        if (showStrefa) {
            notifyItemChanged(0);
        }
    }

    public void setData(List<SubjectDetails> newCourses, boolean showStrefa, String majorName) {
        this.courses.clear();
        if (newCourses != null) {
            this.courses.addAll(newCourses);
        }
        this.showStrefa = showStrefa;
        this.majorName = majorName != null ? majorName : "";
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (showStrefa && position == 0) {
            return TYPE_STREFA;
        }
        return TYPE_COURSE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_STREFA) {
            View view = inflater.inflate(R.layout.item_strefa_card, parent, false);
            return new StrefaViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_all_course_card, parent, false);
            return new CourseViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof StrefaViewHolder) {
            ((StrefaViewHolder) holder).bind(majorName, isStrefaLoading, onStrefaClickListener);
        } else if (holder instanceof CourseViewHolder) {
            int courseIndex = showStrefa ? position - 1 : position;
            if (courseIndex >= 0 && courseIndex < courses.size()) {
                ((CourseViewHolder) holder).bind(courses.get(courseIndex));
            }
        }
    }

    @Override
    public int getItemCount() {
        return courses.size() + (showStrefa ? 1 : 0);
    }

    static class StrefaViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView strefaCard;
        private final TextView textStrefaTitle;
        private final TextView textStrefaDesc;
        private final ImageView strefaArrowIcon;
        private final ProgressBar strefaLoadingProgress;

        StrefaViewHolder(@NonNull View itemView) {
            super(itemView);
            strefaCard = itemView.findViewById(R.id.strefa_card);
            textStrefaTitle = itemView.findViewById(R.id.text_strefa_title);
            textStrefaDesc = itemView.findViewById(R.id.text_strefa_desc);
            strefaArrowIcon = itemView.findViewById(R.id.strefa_arrow_icon);
            strefaLoadingProgress = itemView.findViewById(R.id.strefa_loading_progress);
        }

        void bind(String major, boolean isLoading, OnStrefaClickListener listener) {
            Context ctx = itemView.getContext();
            String title;
            if (major != null && major.toLowerCase(Locale.ROOT).contains("rok zerowy")) {
                title = ctx.getString(R.string.all_courses_strefa_zero_year_title);
            } else {
                title = ctx.getString(R.string.all_courses_strefa_title);
                if (major != null && !major.trim().isEmpty()) {
                    title += " – " + major.trim();
                }
            }
            textStrefaTitle.setText(title);
            textStrefaDesc.setText(R.string.all_courses_strefa_desc);

            if (strefaArrowIcon != null) {
                strefaArrowIcon.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
            }
            if (strefaLoadingProgress != null) {
                if (isLoading) {
                    strefaLoadingProgress.setVisibility(View.VISIBLE);
                    strefaLoadingProgress.animate().alpha(1f).setDuration(150).start();
                } else {
                    strefaLoadingProgress.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                        strefaLoadingProgress.setVisibility(View.GONE);
                    }).start();
                }
            }

            strefaCard.setEnabled(!isLoading);
            strefaCard.setOnClickListener(v -> {
                if (listener != null && !isLoading) {
                    listener.onStrefaClick();
                }
            });
        }
    }

    class CourseViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final View statusStripe;
        private final SegmentedProgressBar segmentedProgressBar;
        private final TextView textProgress;
        private final ImageView iconHasNote;
        private final TextView badgeLessonType;
        private final TextView textCourseName;
        private final View rowTeacher;
        private final TextView textTeacher;
        private final TextView textLocation;
        private final ImageView offsiteArrowIcon;

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.course_card);
            statusStripe = itemView.findViewById(R.id.course_status_stripe);
            segmentedProgressBar = itemView.findViewById(R.id.segmented_progress_bar);
            textProgress = itemView.findViewById(R.id.text_progress);
            iconHasNote = itemView.findViewById(R.id.icon_has_note);
            badgeLessonType = itemView.findViewById(R.id.badge_lesson_type);
            textCourseName = itemView.findViewById(R.id.text_course_name);
            rowTeacher = itemView.findViewById(R.id.row_teacher);
            textTeacher = itemView.findViewById(R.id.text_teacher);
            textLocation = itemView.findViewById(R.id.text_location);
            offsiteArrowIcon = itemView.findViewById(R.id.offsite_arrow_icon);
        }

        void bind(SubjectDetails details) {
            if (details == null) return;
            LocalDateTime now = LocalDateTime.now();

            int completed = details.getCompletedCount(now);
            int total = details.getTotalCount();
            boolean isCompleted = total > 0 && completed >= total;

            statusStripe.setBackgroundColor(isCompleted ? stripePastColor : stripeActiveColor);
            segmentedProgressBar.setProgress(completed, total);
            textProgress.setText(completed + " / " + total);

            if (iconHasNote != null) {
                boolean hasNote = SubjectDetailsActivity.hasSavedNote(
                        context,
                        details.getSubjectName(),
                        details.getLessonType(),
                        details.getRawLessonType()
                );
                iconHasNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
            }

            String rawType = details.getRawLessonType();
            LessonType resolvedType = details.getLessonType();
            String displayType = (resolvedType != null && resolvedType != LessonType.INNE)
                    ? resolvedType.getLocalizedDisplayName(context)
                    : ((rawType != null && !rawType.trim().isEmpty()) ? rawType.trim() : null);

            if (displayType != null && !displayType.trim().isEmpty()) {
                badgeLessonType.setVisibility(View.VISIBLE);
                badgeLessonType.setText(displayType.toUpperCase(Locale.getDefault()));
                badgeLessonType.setBackgroundResource(R.drawable.bg_badge_type);
                badgeLessonType.setTextColor(badgeTypeTextColor);
            } else {
                badgeLessonType.setVisibility(View.GONE);
            }

            textCourseName.setText(details.getSubjectName());

            if (details.hasTeacher()) {
                rowTeacher.setVisibility(View.VISIBLE);
                textTeacher.setText(details.getTeacherName().trim());
            } else {
                rowTeacher.setVisibility(View.GONE);
            }

            Location loc = details.getDominantLocation();
            textLocation.setText(loc != null ? loc.getDisplayText() : "—");

            if (loc != null && loc.hasMapLink()) {
                offsiteArrowIcon.setVisibility(View.VISIBLE);
                View.OnClickListener mapClick = v -> {
                    String query = loc.getMapQueryUrl();
                    if (query != null) {
                        WidgetActionHelper.openLocationInMaps(context, query);
                    }
                };
                textLocation.setOnClickListener(mapClick);
                offsiteArrowIcon.setOnClickListener(mapClick);
            } else {
                offsiteArrowIcon.setVisibility(View.GONE);
                textLocation.setOnClickListener(null);
                offsiteArrowIcon.setOnClickListener(null);
            }

            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(context, SubjectDetailsActivity.class);
                intent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, details.getSubjectName());
                intent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, details.getRawLessonType());
                context.startActivity(intent);
            });
        }
    }
}
