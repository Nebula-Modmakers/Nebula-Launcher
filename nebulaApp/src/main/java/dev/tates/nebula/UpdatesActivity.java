package dev.tates.nebula;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class UpdatesActivity extends Activity {
    private View root;
    private LinearLayout content;
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); root = screen(); setContentView(root); Utilities.applyWindowInsets(root, dp(12)); UiMotion.enter(root); check();
    }

    private View screen() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundResource(R.drawable.bg_nebula_screen);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this); back.setImageResource(R.drawable.ic_back); back.setBackgroundResource(R.drawable.bg_nebula_button); back.setContentDescription("Back"); back.setOnClickListener(v -> UiMotion.finish(this, root));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("Available Updates", 28, 0xFFF0F3FF, true); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f); tp.leftMargin = dp(18); header.addView(title, tp); page.addView(header);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, dp(18), 0, dp(24)); scroll.addView(content); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return page;
    }

    private void check() {
        content.removeAllViews(); ProgressBar progress = new ProgressBar(this); content.addView(progress, centered(dp(48), dp(48)));
        TextView status = label("Checking the Nebula API…", 14, 0xFFAAB4E8, false); status.setGravity(Gravity.CENTER); content.addView(status, top(dp(12)));
        executor.execute(() -> {
            AppUpdateClient.Release appRelease = null;
            List<ModUpdate> modUpdates = new ArrayList<>();
            String appError = null;
            String modError = null;
            try { appRelease = AppUpdateClient.check(this); }
            catch (Exception error) { appError = error.getMessage(); }
            try { modUpdates = findModUpdates(ModCatalogClient.fetchCatalog()); }
            catch (Exception error) { modError = error.getMessage(); }
            AppUpdateClient.Release finalAppRelease = appRelease;
            List<ModUpdate> finalModUpdates = modUpdates;
            String finalAppError = appError;
            String finalModError = modError;
            runOnUiThread(() -> showResult(finalAppRelease, finalModUpdates, finalAppError, finalModError));
        });
    }

    private void showResult(AppUpdateClient.Release release, List<ModUpdate> modUpdates,
            String appError, String modError) {
        content.removeAllViews();
        if (appError != null) {
            card("Could not check Nebula", safe(appError), null);
        } else if (release == null) { card("Nebula is up to date", "There are no app updates available.", null); }
        else {
            String body = "Version " + release.versionName + " is available (" + formatSize(release.size) + ").";
            if (!release.notes.trim().isEmpty()) body += "\n\n" + release.notes.trim();
            Button update = button("Download and install"); update.setOnClickListener(v -> install(update, release)); card("Nebula " + release.versionName, body, update);
        }
        if (modError != null) {
            card("Could not check mod updates", safe(modError), null);
        } else if (modUpdates.isEmpty()) {
            card("Mods are up to date", "All installed Mod Store packages use their latest available versions.", null);
        } else {
            for (ModUpdate update : modUpdates) {
                Button openStore = button("Open Mod Store");
                openStore.setOnClickListener(view -> startActivity(
                        new Intent(this, ModStoreActivity.class)
                                .putExtra(ModStoreActivity.EXTRA_MOD_ID, update.mod.id)));
                card(update.mod.name + " " + update.mod.latestVersion,
                        "Update recommended: " + update.installedVersion + " → "
                                + update.mod.latestVersion + ".", openStore);
            }
        }
        Button refresh = button("Check again"); refresh.setOnClickListener(v -> check()); content.addView(refresh, top(dp(16)));
    }

    private List<ModUpdate> findModUpdates(List<ModCatalogClient.Mod> mods) {
        List<ModUpdate> updates = new ArrayList<>();
        for (ModCatalogClient.Mod mod : mods) {
            String installed = NpkgInstaller.getInstalledVersion(this, mod.packageId);
            if (installed == null || installed.equals(mod.latestVersion)) continue;
            if (isNewerVersion(mod.latestVersion, installed)) updates.add(new ModUpdate(mod, installed));
        }
        return updates;
    }

    private boolean isNewerVersion(String available, String installed) {
        String[] availableParts = available.split("[^0-9]+");
        String[] installedParts = installed.split("[^0-9]+");
        int count = Math.max(availableParts.length, installedParts.length);
        for (int i = 0; i < count; i++) {
            long left = i < availableParts.length && !availableParts[i].isEmpty()
                    ? Long.parseLong(availableParts[i]) : 0L;
            long right = i < installedParts.length && !installedParts[i].isEmpty()
                    ? Long.parseLong(installedParts[i]) : 0L;
            if (left != right) return left > right;
        }
        boolean availablePrerelease = available.contains("-");
        boolean installedPrerelease = installed.contains("-");
        return installedPrerelease && !availablePrerelease;
    }

    private static final class ModUpdate {
        final ModCatalogClient.Mod mod;
        final String installedVersion;
        ModUpdate(ModCatalogClient.Mod mod, String installedVersion) {
            this.mod = mod;
            this.installedVersion = installedVersion;
        }
    }

    private void install(Button button, AppUpdateClient.Release release) {
        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setPadding(dp(24), dp(4), dp(24), dp(4));
        TextView status = label("Preparing Nebula " + release.versionName + "…", 16, 0xFFAAB4E8, false);
        loading.addView(status, new LinearLayout.LayoutParams(-1, -2));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF63E6FF));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF26345F));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(10));
        progressParams.topMargin = dp(22);
        loading.addView(progress, progressParams);
        AlertDialog dialog = new NebulaDialogBuilder(this)
                .setTitle("Updating Nebula")
                .setView(loading)
                .setNegativeButton("Hide", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        button.setEnabled(false); button.setText("Downloading…");
        executor.execute(() -> {
            try {
                File apk = AppUpdateClient.downloadAndVerify(this, release, (phase, completed, total) ->
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            status.setText(phase + (total > 0 ? "  •  " + formatSize(completed)
                                    + " / " + formatSize(total) : "…"));
                            progress.setIndeterminate(total <= 0);
                            if (total > 0) progress.setProgress((int) Math.min(100, completed * 100L / total));
                        }));
                runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    launchInstaller(apk, button);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    button.setEnabled(true); button.setText("Download and install"); showErrorDialog(e.getMessage());
                });
            }
        });
    }

    private void launchInstaller(File apk, Button button) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            button.setEnabled(true); button.setText("Continue installation");
            new NebulaDialogBuilder(this)
                    .setTitle("Allow Nebula to install this update?")
                    .setMessage("Android requires permission for Nebula to hand its verified APK to the system package installer. This permission is used only when you choose an app update here.")
                    .setPositiveButton("Open Android Settings", (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent); button.setEnabled(true); button.setText("Download and install");
    }

    private void showError(String message) { content.removeAllViews(); card("Could not check for updates", safe(message), null); Button retry = button("Try again"); retry.setOnClickListener(v -> check()); content.addView(retry, top(dp(16))); }
    private void showErrorDialog(String message) { new NebulaDialogBuilder(this).setTitle("Update failed").setMessage(safe(message)).setPositiveButton("Done", null).show(); }
    private String safe(String value) { return value == null || value.trim().isEmpty() ? "Please try again." : value; }

    private void card(String title, String body, Button action) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackgroundResource(R.drawable.bg_nebula_card); card.addView(label(title, 20, 0xFFF0F3FF, true)); card.addView(label(body, 14, 0xFFAAB4E8, false), top(dp(8))); if (action != null) card.addView(action, top(dp(18))); content.addView(card, top(dp(12))); }
    private Button button(String text) { Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextColor(0xFFF0F3FF); button.setBackgroundResource(R.drawable.bg_nebula_button); return button; }
    private TextView label(String text, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return view; }
    private LinearLayout.LayoutParams top(int value) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = value; return p; }
    private LinearLayout.LayoutParams centered(int w, int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.gravity = Gravity.CENTER_HORIZONTAL; return p; }
    private String formatSize(long bytes) { return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { UiMotion.finish(this, root); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
