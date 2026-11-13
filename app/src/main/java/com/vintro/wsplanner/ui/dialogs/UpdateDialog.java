package com.vintro.wsplanner.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.services.UpdateManager;
import com.vintro.wsplanner.utils.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UpdateDialog {
    private static final String TAG = "UpdateDialog";

    public static void show(@NonNull Context context, @Nullable String newVersion, @Nullable String releaseUrl) {
        show(context, newVersion, releaseUrl, UpdateManager.getSavedApkUrl(context));
    }

    public static void show(@NonNull Context context, @Nullable String newVersion, @Nullable String releaseUrl, @Nullable String apkUrl) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            Logger.w(TAG, "Activity is finishing, cannot show update dialog");
            return;
        }

        String targetUrl = releaseUrl != null && !releaseUrl.isEmpty() ? releaseUrl : UpdateManager.GITHUB_RELEASES_URL;
        Logger.i(TAG, "Displaying update dialog: newVersion=" + newVersion + ", targetUrl=" + targetUrl + ", apkUrl=" + apkUrl);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_update_available, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.60f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(24);
            }
        }

        TextView currentBadge = view.findViewById(R.id.dialog_current_version_badge);
        TextView newBadge = view.findViewById(R.id.dialog_new_version_badge);
        ImageButton btnClose = view.findViewById(R.id.dialog_update_close);
        MaterialButton btnAutoUpdate = view.findViewById(R.id.dialog_btn_auto_update);
        ProgressBar progressAutoUpdate = view.findViewById(R.id.dialog_auto_update_progress);
        LinearLayout errorCard = view.findViewById(R.id.dialog_update_error_card);
        MaterialButton btnOpenGithub = view.findViewById(R.id.dialog_btn_open_github);
        MaterialButton btnLater = view.findViewById(R.id.dialog_btn_later);

        NestedScrollView changelogScroll = view.findViewById(R.id.dialog_changelog_scroll);
        LinearLayout changelogContainer = view.findViewById(R.id.dialog_changelog_container);
        TextView fallbackDesc = view.findViewById(R.id.dialog_update_desc);

        // Constrain scroll container max height dynamically based on screen height
        if (changelogScroll != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int maxScrollHeight = (int) (dm.heightPixels * 0.36f);
            int minThreshold = (int) (220 * dm.density);
            int finalMaxHeight = Math.max(maxScrollHeight, minThreshold);

            changelogScroll.post(() -> {
                if (changelogScroll.getHeight() > finalMaxHeight) {
                    ViewGroup.LayoutParams lp = changelogScroll.getLayoutParams();
                    lp.height = finalMaxHeight;
                    changelogScroll.setLayoutParams(lp);
                }
            });
        }

        // Current and new version badges
        String currentVerStr = BuildConfig.VERSION_NAME;
        if (!currentVerStr.startsWith("v") && !currentVerStr.startsWith("V")) {
            currentVerStr = "v" + currentVerStr;
        }
        if (currentBadge != null) {
            currentBadge.setText(formatBadgeText(context, R.string.update_dialog_current_badge, currentVerStr));
        }

        String newVerStr = newVersion != null ? newVersion.trim() : "";
        if (!newVerStr.startsWith("v") && !newVerStr.startsWith("V") && !newVerStr.isEmpty()) {
            newVerStr = "v" + newVerStr;
        }
        if (newBadge != null) {
            newBadge.setText(formatBadgeText(context, R.string.update_dialog_new_badge, newVerStr));
        }

        // Populate changelog
        String savedChangelog = UpdateManager.getSavedChangelog(context);
        populateChangelog(context, inflater, changelogContainer, fallbackDesc, savedChangelog, newVerStr);

        // Auto update action
        if (btnAutoUpdate != null) {
            btnAutoUpdate.setOnClickListener(v -> {
                Logger.i(TAG, "Update dialog: user clicked 'Update automatically' button for version: " + newVersion);
                if (errorCard != null) {
                    errorCard.setVisibility(View.GONE);
                }
                btnAutoUpdate.setEnabled(false);
                btnAutoUpdate.setText(R.string.update_dialog_downloading);
                if (progressAutoUpdate != null) {
                    progressAutoUpdate.setVisibility(View.VISIBLE);
                }
                if (btnOpenGithub != null) btnOpenGithub.setEnabled(false);
                if (btnLater != null) btnLater.setEnabled(false);
                if (btnClose != null) btnClose.setEnabled(false);
                dialog.setCancelable(false);

                String resolvedApkUrl = apkUrl != null && !apkUrl.isEmpty() ? apkUrl : UpdateManager.getSavedApkUrl(context);

                UpdateManager.downloadAndInstallApk(context, newVersion, resolvedApkUrl, new UpdateManager.DownloadInstallCallback() {
                    @Override
                    public void onDownloadStarted() {
                        Logger.d(TAG, "Auto-update download stream started");
                    }

                    @Override
                    public void onVerifyingHash() {
                        Logger.d(TAG, "Auto-update switching UI state to verifying hash");
                        btnAutoUpdate.setText(R.string.update_dialog_verifying_hash);
                    }

                    @Override
                    public void onSuccess() {
                        Logger.i(TAG, "Auto-update succeeded: installer dispatched. Dismissing update dialog");
                        dialog.dismiss();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Logger.w(TAG, "Auto-update failed: " + Logger.formatException(e));
                        if (progressAutoUpdate != null) {
                            progressAutoUpdate.setVisibility(View.GONE);
                        }
                        btnAutoUpdate.setText(R.string.update_dialog_button_auto);
                        btnAutoUpdate.setEnabled(true);
                        if (btnOpenGithub != null) btnOpenGithub.setEnabled(true);
                        if (btnLater != null) btnLater.setEnabled(true);
                        if (btnClose != null) btnClose.setEnabled(true);
                        dialog.setCancelable(true);
                        if (errorCard != null) {
                            errorCard.setVisibility(View.VISIBLE);
                        }
                    }
                });
            });
        }

        // Open GitHub button
        if (btnOpenGithub != null) {
            btnOpenGithub.setOnClickListener(v -> {
                Logger.i(TAG, "Update dialog: user clicked 'Go to GitHub' button -> targetUrl: " + targetUrl);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                    Logger.i(TAG, "Update dialog: successfully launched browser intent for release URL: " + targetUrl);
                } catch (Exception e) {
                    Logger.e(TAG, "Update dialog: failed to open release URL: " + targetUrl + " | " + Logger.formatException(e), e);
                }
                dialog.dismiss();
            });
        }

        // Later button
        if (btnLater != null) {
            btnLater.setOnClickListener(v -> {
                Logger.i(TAG, "Update dialog: dismissed by Later button");
                dialog.dismiss();
            });
        }

        // Close button
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                Logger.i(TAG, "Update dialog: dismissed by close button");
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private static void populateChangelog(
            @NonNull Context context,
            @NonNull LayoutInflater inflater,
            @Nullable LinearLayout container,
            @Nullable TextView fallbackDesc,
            @Nullable String savedChangelog,
            @NonNull String targetVersion
    ) {
        if (container == null) return;

        if (savedChangelog == null || savedChangelog.trim().isEmpty()) {
            if (fallbackDesc != null) {
                fallbackDesc.setVisibility(View.VISIBLE);
                fallbackDesc.setText(R.string.update_dialog_desc);
            }
            return;
        }

        String trimmed = savedChangelog.trim();

        // Check if savedChangelog is a JSON array of version objects
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(trimmed);
                if (array.length() > 0) {
                    if (fallbackDesc != null) {
                        fallbackDesc.setVisibility(View.GONE);
                    }
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject verObj = array.getJSONObject(i);
                        String verTag = verObj.optString("version", targetVersion);
                        String verTitle = verObj.optString("title", "");
                        JSONArray itemsJson = verObj.optJSONArray("items");

                        List<String> items = new ArrayList<>();
                        if (itemsJson != null) {
                            for (int j = 0; j < itemsJson.length(); j++) {
                                String item = itemsJson.optString(j, "").trim();
                                if (!item.isEmpty()) {
                                    items.add(item);
                                }
                            }
                        }

                        boolean isLast = (i == array.length() - 1);
                        renderVersionItem(context, inflater, container, verTag, verTitle, items, isLast);
                    }
                    return;
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to parse structured changelog JSON: " + e.getMessage());
            }
        }

        // Legacy / plain-text changelog format
        List<String> bulletItems = UpdateManager.parseBulletLines(trimmed);
        if (!bulletItems.isEmpty()) {
            if (fallbackDesc != null) {
                fallbackDesc.setVisibility(View.GONE);
            }
            renderVersionItem(context, inflater, container, targetVersion, "", bulletItems, true);
        } else {
            if (fallbackDesc != null) {
                fallbackDesc.setVisibility(View.VISIBLE);
                fallbackDesc.setText(trimmed);
            }
        }
    }

    private static void renderVersionItem(
            @NonNull Context context,
            @NonNull LayoutInflater inflater,
            @NonNull LinearLayout container,
            @NonNull String versionTag,
            @NonNull String versionTitle,
            @NonNull List<String> items,
            boolean isLast
    ) {
        View versionView = inflater.inflate(R.layout.item_update_version_changelog, container, false);

        TextView tagBadge = versionView.findViewById(R.id.version_tag_badge);
        TextView titleText = versionView.findViewById(R.id.version_title);
        LinearLayout itemsContainer = versionView.findViewById(R.id.version_items_container);
        TextView legacyNote = versionView.findViewById(R.id.version_legacy_note);
        View divider = versionView.findViewById(R.id.version_divider);

        String cleanTag = versionTag.startsWith("v") || versionTag.startsWith("V") ? versionTag : "v" + versionTag;
        tagBadge.setText(cleanTag);

        String cleanTitle = versionTitle != null ? versionTitle.trim() : "";
        if (!cleanTitle.isEmpty() && !cleanTitle.equalsIgnoreCase(cleanTag) && !cleanTitle.equalsIgnoreCase(versionTag)) {
            titleText.setText(cleanTitle);
            titleText.setVisibility(View.VISIBLE);
        } else {
            titleText.setVisibility(View.GONE);
        }

        if (!items.isEmpty()) {
            itemsContainer.setVisibility(View.VISIBLE);
            for (String itemText : items) {
                View bulletView = inflater.inflate(R.layout.item_update_changelog_bullet, itemsContainer, false);
                TextView txt = bulletView.findViewById(R.id.bullet_text);
                txt.setText(itemText);
                itemsContainer.addView(bulletView);
            }
        } else {
            itemsContainer.setVisibility(View.GONE);
        }
        if (legacyNote != null) {
            legacyNote.setVisibility(View.GONE);
        }

        if (divider != null) {
            divider.setVisibility(isLast ? View.GONE : View.VISIBLE);
        }

        container.addView(versionView);
    }

    private static String formatBadgeText(@NonNull Context context, int resId, @NonNull String version) {
        String template = context.getString(resId);
        if (template.contains("%1$s")) {
            return template.replace("%1$s", version);
        } else if (template.contains("%s")) {
            return template.replace("%s", version);
        }
        return template.trim() + " " + version;
    }
}
