package com.vintro.wsplanner.ui.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// helper utility for widget state, theme resolution, and maps launching
public class WidgetActionHelper {

    private static final DateTimeFormatter NEXT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter NEXT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // check whether onboarding and schedule profile configuration are complete
    public static boolean isScheduleConfigured(Context context) {
        return context != null
                && PreferencesManager.isOnboardingCompleted(context)
                && ScheduleRepository.getInstance(context).isConfigurationComplete();
    }

    // find the next scheduled lesson after now for empty-state widget previews
    public static Lesson findNextUpcomingLesson(Context context, LocalDateTime now) {
        if (context == null || now == null) return null;
        try {
            Lesson upcoming = ScheduleRepository.getInstance(context).findNextLessonAfter(now);
            if (upcoming == null || upcoming.getDate() == null || upcoming.getStartTime() == null) {
                return null;
            }
            if (upcoming.getSubjectName() == null || upcoming.getSubjectName().trim().isEmpty()) {
                return null;
            }
            return upcoming;
        } catch (Exception e) {
            Logger.w("WidgetActionHelper.findNextUpcomingLesson", "Failed resolving next upcoming lesson: " + Logger.formatException(e));
            return null;
        }
    }

    // apply light or dark theme colors to the shared empty-state views
    public static void applyEmptyStateTheme(Context context, RemoteViews views, boolean isLight) {
        views.setTextColor(R.id.widget_empty_title, ContextCompat.getColor(context,
                isLight ? R.color.widget_text_light_primary : R.color.widget_text_dark_primary));
        views.setTextColor(R.id.widget_empty_next_header, ContextCompat.getColor(context,
                isLight ? R.color.widget_badge_type_text_light : R.color.badge_now_text));
        views.setTextColor(R.id.widget_empty_subtitle, ContextCompat.getColor(context,
                isLight ? R.color.widget_text_light_secondary : R.color.widget_text_dark_secondary));
    }

    // populate shared empty state for incomplete onboarding setup
    public static void bindEmptyStateSetup(Context context, RemoteViews views) {
        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE);
        views.setViewVisibility(R.id.widget_empty_next_header, View.GONE);
        views.setViewVisibility(R.id.widget_empty_subtitle, View.VISIBLE);
        views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_setup_required_title));
        views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.widget_setup_required_desc));
        views.setInt(R.id.widget_empty_subtitle, "setMaxLines", 2);
    }

    // populate shared empty state with a subtle multi-line preview of the next upcoming lesson
    public static void bindEmptyStateNoLessons(Context context, RemoteViews views, String title, LocalDateTime now, int maxSubjectLines) {
        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE);
        views.setTextViewText(R.id.widget_empty_title, title);

        Lesson upcoming = findNextUpcomingLesson(context, now);
        if (upcoming != null) {
            String dateStr = upcoming.getDate().format(NEXT_DATE_FORMATTER);
            String timeStr = upcoming.getStartTime().format(NEXT_TIME_FORMATTER);
            views.setTextViewText(R.id.widget_empty_next_header, context.getString(R.string.widget_next_class_header, dateStr, timeStr));
            views.setViewVisibility(R.id.widget_empty_next_header, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty_subtitle, upcoming.getSubjectName().trim());
            views.setInt(R.id.widget_empty_subtitle, "setMaxLines", maxSubjectLines);
            views.setViewVisibility(R.id.widget_empty_subtitle, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_empty_next_header, View.GONE);
            views.setViewVisibility(R.id.widget_empty_subtitle, View.GONE);
        }
    }

    // resolve whether widgets should render with light colors for the saved preference
    public static boolean isWidgetLightTheme(Context context) {
        return isWidgetLightTheme(context, PreferencesManager.getWidgetTheme(context));
    }

    // resolve whether widgets should render with light colors for a specific theme constant
    public static boolean isWidgetLightTheme(Context context, String theme) {
        if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
            return true;
        }
        if (PreferencesManager.WIDGET_THEME_DARK.equals(theme)) {
            return false;
        }
        // system or auto follows the device night mode independently of in-app overrides
        int nightModeFlags = context.getApplicationContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags != Configuration.UI_MODE_NIGHT_YES;
    }

    // launch maps app for geo uri or fall back to browser https google maps on hms devices
    public static void openLocationInMaps(Context context, String query) {
        if (context == null || query == null || query.trim().isEmpty()) return;
        String trimmed = query.trim();
        Uri uri = Uri.parse(trimmed);
        String scheme = uri.getScheme();
        boolean isAllowed = false;
        if ("geo".equalsIgnoreCase(scheme)) {
            isAllowed = true;
        } else if ("https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host != null) {
                String lowerHost = host.toLowerCase(Locale.ROOT);
                isAllowed = lowerHost.equals("maps.google.com")
                        || lowerHost.equals("www.google.com")
                        || lowerHost.equals("google.com")
                        || lowerHost.equals("maps.app.goo.gl");
            }
        }
        if (!isAllowed) {
            Logger.w("WidgetActionHelper.openLocationInMaps", "Blocked untrusted map URI: " + trimmed);
            return;
        }

        try {
            Logger.d("WidgetActionHelper.openLocationInMaps", "Opening maps with query: " + trimmed);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
            if (!(context instanceof Activity)) {
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            Logger.w("WidgetActionHelper.openLocationInMaps", "No maps app for geo URI, trying browser fallback: " + Logger.formatException(e));
            String webFallbackUrl = buildBrowserFallbackUrl(trimmed);
            if (webFallbackUrl != null) {
                try {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl));
                    if (!(context instanceof Activity)) {
                        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(webIntent);
                } catch (Exception ex) {
                    Logger.e("WidgetActionHelper.openLocationInMaps", "Browser fallback also failed: " + Logger.formatException(ex));
                }
            }
        } catch (Exception e) {
            Logger.e("WidgetActionHelper.openLocationInMaps", "Error opening map: " + Logger.formatException(e));
        }
    }

    private static String buildBrowserFallbackUrl(String rawUri) {
        if (rawUri == null) return null;
        int qIndex = rawUri.indexOf("q=");
        if (rawUri.toLowerCase(Locale.ROOT).startsWith("geo:") && qIndex >= 0) {
            String encodedQuery = rawUri.substring(qIndex + 2);
            if (!encodedQuery.isEmpty()) {
                return "https://www.google.com/maps/search/?api=1&query=" + encodedQuery;
            }
        }
        return null;
    }
}
