package dev.tates.nebula;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModStoreActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private View root;
    private View catalog;
    private View detail;
    private ModCatalogClient.Mod selectedMod;
    private ModCatalogClient.Version selectedVersion;
    private volatile boolean installing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod_store);
        root = findViewById(R.id.mod_store_root);
        catalog = findViewById(R.id.mod_store_catalog);
        detail = findViewById(R.id.mod_store_detail);
        Utilities.applyWindowInsets(root, 0);

        findViewById(R.id.mod_store_back).setOnClickListener(view -> UiMotion.finish(this, root));
        findViewById(R.id.mod_detail_back).setOnClickListener(view -> showCatalog());
        findViewById(R.id.mod_detail_install).setOnClickListener(view -> {
            if (selectedMod != null
                    && NpkgInstaller.getInstalledVersion(this, selectedMod.packageId) != null) {
                confirmUninstall();
            } else {
                confirmInstall();
            }
        });
        UiMotion.press(findViewById(R.id.mod_store_back));
        UiMotion.press(findViewById(R.id.mod_detail_back));
        UiMotion.press(findViewById(R.id.mod_detail_install));

        loadCatalog();
        UiMotion.enter(root);
        registerPredictiveBack();
    }

    private void loadCatalog() {
        LinearLayout list = findViewById(R.id.mod_store_list);
        list.removeAllViews();
        list.addView(statusCard("Loading the Nebula catalog\u2026", null));
        worker.execute(() -> {
            try {
                List<ModCatalogClient.Mod> mods = ModCatalogClient.fetchCatalog();
                runOnUiThread(() -> showCatalogResults(mods));
            } catch (Throwable error) {
                runOnUiThread(() -> showCatalogError(error));
            }
        });
    }

    private void showCatalogResults(List<ModCatalogClient.Mod> mods) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout list = findViewById(R.id.mod_store_list);
        list.removeAllViews();
        if (mods.isEmpty()) {
            list.addView(statusCard("No Android-compatible mods are available yet.", "Refresh"));
            return;
        }
        for (ModCatalogClient.Mod mod : mods) list.addView(createModCard(mod));
        list.post(() -> UiMotion.stagger(list));
    }

    private void showCatalogError(Throwable error) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout list = findViewById(R.id.mod_store_list);
        list.removeAllViews();
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = "Could not reach the mod catalog.";
        list.addView(statusCard(message, "Try again"));
    }

    private View statusCard(String message, String action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(26), dp(40), dp(26), dp(40));
        card.setBackgroundResource(R.drawable.bg_nebula_card);
        TextView label = text(message, 16, 0xFFAAB4E8, false);
        label.setGravity(Gravity.CENTER);
        card.addView(label);
        if (action != null) {
            Button retry = new Button(this);
            retry.setText(action);
            retry.setTransformationMethod(null);
            retry.setTextColor(Color.WHITE);
            retry.setTextSize(16);
            retry.setBackgroundResource(R.drawable.bg_nebula_accent_button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            params.topMargin = dp(20);
            card.addView(retry, params);
            retry.setOnClickListener(view -> loadCatalog());
            UiMotion.press(retry);
        }
        return card;
    }

    private View createModCard(ModCatalogClient.Mod mod) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        card.setBackgroundResource(R.drawable.bg_nebula_card);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = text(mod.badge, 18, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_nebula_icon);
        heading.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        heading.addView(labels, labelsParams);
        labels.addView(text(mod.name, 20, 0xFFF0F3FF, true));
        TextView author = text("by " + mod.author, 13, 0xFF8790B9, false);
        author.setPadding(0, dp(3), 0, 0);
        labels.addView(author);

        TextView category = text(mod.category, 14, 0xFF63E6FF, false);
        category.setPadding(0, dp(16), 0, dp(16));
        card.addView(category);

        Button install = new Button(this);
        String installedVersion = NpkgInstaller.getInstalledVersion(this, mod.packageId);
        install.setText(installedVersion == null
                ? "Install latest  \u2022  " + mod.latest().version
                : "Uninstall  \u2022  " + installedVersion);
        install.setTransformationMethod(null);
        install.setTextColor(Color.WHITE);
        install.setTextSize(17);
        install.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        install.setBackgroundResource(R.drawable.bg_nebula_accent_button);
        card.addView(install, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        card.setOnClickListener(view -> showDetail(mod));
        install.setOnClickListener(view -> {
            selectedMod = mod;
            selectedVersion = mod.latest();
            if (NpkgInstaller.getInstalledVersion(this, mod.packageId) != null) {
                confirmUninstall();
            } else {
                confirmInstall();
            }
        });
        UiMotion.press(card);
        UiMotion.press(install);
        return card;
    }

    private void showDetail(ModCatalogClient.Mod mod) {
        selectedMod = mod;
        selectedVersion = mod.latest();
        ((TextView) findViewById(R.id.mod_detail_header)).setText("Mod details");
        ((TextView) findViewById(R.id.mod_detail_badge)).setText(mod.badge);
        ((TextView) findViewById(R.id.mod_detail_title)).setText(mod.name);
        ((TextView) findViewById(R.id.mod_detail_author))
                .setText("by " + mod.author + "  \u2022  Android compatible");
        ((TextView) findViewById(R.id.mod_detail_description)).setText(mod.description);

        RadioGroup versions = findViewById(R.id.mod_detail_versions);
        versions.removeAllViews();
        for (int i = 0; i < mod.versions.size(); i++) {
            ModCatalogClient.Version version = mod.versions.get(i);
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(version);
            option.setText((version == mod.latest() ? "Latest  \u2022  " : "Version  ")
                    + version.version + "  \u2022  " + formatBytes(version.size));
            option.setTextColor(0xFFF0F3FF);
            option.setTextSize(16);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(18), 0, dp(18), 0);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF63E6FF));
            option.setBackgroundResource(R.drawable.bg_nebula_button);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
            params.bottomMargin = dp(9);
            versions.addView(option, params);
            if (version == selectedVersion) option.setChecked(true);
            UiMotion.press(option);
        }
        versions.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof ModCatalogClient.Version) {
                selectedVersion = (ModCatalogClient.Version) checked.getTag();
                updatePrimaryAction();
            }
        });

        updatePrimaryAction();
        transition(catalog, detail);
    }

    private void updatePrimaryAction() {
        if (selectedMod == null) return;
        String installedVersion = NpkgInstaller.getInstalledVersion(this, selectedMod.packageId);
        updateInstallButton(installedVersion == null
                ? "Install " + selectedMod.name
                : "Uninstall " + selectedMod.name + "  \u2022  " + installedVersion, true);
    }

    private void confirmInstall() {
        if (selectedMod == null || selectedVersion == null || installing) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(4), dp(24), dp(4));
        TextView status = text("Version " + selectedVersion.version + "  \u2022  "
                + formatBytes(selectedVersion.size) + "\n\nThis package will be verified "
                + "and installed into the " + ProfileManager.getActiveName(this)
                + " profile.", 16, 0xFFAAB4E8, false);
        status.setLineSpacing(0f, 1.15f);
        content.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xFF63E6FF));
        progress.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF26345F));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        progressParams.topMargin = dp(22);
        content.addView(progress, progressParams);

        AlertDialog dialog = new NebulaDialogBuilder(this)
                .setTitle("Install " + selectedMod.name + "?")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Install", null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                view -> beginInstall(dialog, status, progress));
    }

    private void confirmUninstall() {
        if (selectedMod == null || installing) return;
        String installedVersion = NpkgInstaller.getInstalledVersion(this, selectedMod.packageId);
        if (installedVersion == null) {
            updatePrimaryAction();
            return;
        }
        ModCatalogClient.Mod mod = selectedMod;
        new NebulaDialogBuilder(this)
                .setTitle("Uninstall " + mod.name + "?")
                .setMessage("This removes version " + installedVersion + " and all files owned by "
                        + "this package from the " + ProfileManager.getActiveName(this)
                        + " profile. Externally added mods are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Uninstall", (dialog, which) -> beginUninstall(mod))
                .show();
    }

    private void beginUninstall(ModCatalogClient.Mod mod) {
        if (installing) return;
        installing = true;
        updateInstallButton("Uninstalling\u2026", false);
        worker.execute(() -> {
            try {
                NpkgInstaller.uninstall(this, mod.packageId);
                runOnUiThread(() -> {
                    installing = false;
                    android.widget.Toast.makeText(this, mod.name + " was uninstalled.",
                            android.widget.Toast.LENGTH_LONG).show();
                    updatePrimaryAction();
                    loadCatalog();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    installing = false;
                    updatePrimaryAction();
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) message = "Uninstall failed";
                    new NebulaDialogBuilder(this)
                            .setTitle("Could not uninstall " + mod.name)
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void beginInstall(AlertDialog dialog, TextView dialogStatus, ProgressBar progressBar) {
        if (installing || selectedMod == null || selectedVersion == null) return;
        installing = true;
        ModCatalogClient.Mod mod = selectedMod;
        ModCatalogClient.Version version = selectedVersion;
        dialog.setTitle("Installing " + mod.name);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.GONE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Installing\u2026");
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        dialogStatus.setText("Connecting to the Nebula catalog\u2026");
        updateInstallButton("Preparing download\u2026", false);
        worker.execute(() -> {
            try {
                NpkgInstaller.downloadAndInstall(this, mod, version,
                        (phase, completed, total) -> runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            int percent = total > 0
                                    ? Math.min(100, Math.round(completed * 100f / total))
                                    : 0;
                            progressBar.setIndeterminate(total <= 0);
                            if (total > 0) progressBar.setProgress(percent);
                            String suffix = total > 0 ? "  " + percent + "%" : "";
                            dialogStatus.setText(phase + suffix + "\n\nKeep Nebula open while "
                                    + "the package is verified and added to your profile.");
                            updateInstallButton(phase + suffix, false);
                        }));
                runOnUiThread(() -> installFinished(dialog, dialogStatus, progressBar, mod, version));
            } catch (Throwable error) {
                runOnUiThread(() -> installFailed(dialog, dialogStatus, progressBar, error));
            }
        });
    }

    private void installFinished(AlertDialog dialog, TextView dialogStatus, ProgressBar progressBar,
            ModCatalogClient.Mod mod, ModCatalogClient.Version version) {
        installing = false;
        updateInstallButton("Installed  \u2022  " + version.version, true);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(100);
        dialog.setTitle("Installed");
        dialogStatus.setText(mod.name + " " + version.version + " was verified and installed "
                + "into the " + ProfileManager.getActiveName(this) + " profile.");
        Button done = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        done.setText("Done");
        done.setEnabled(true);
        done.setOnClickListener(view -> dialog.dismiss());
        dialog.setCancelable(true);
    }

    private void installFailed(AlertDialog dialog, TextView dialogStatus, ProgressBar progressBar,
            Throwable error) {
        installing = false;
        updateInstallButton(selectedMod == null ? "Install" : "Install " + selectedMod.name, true);
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        progressBar.setVisibility(View.GONE);
        dialog.setTitle("Installation failed");
        dialogStatus.setText(message + "\n\nNo unverified package files were installed.");
        Button back = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        back.setText("Back");
        back.setEnabled(true);
        back.setOnClickListener(view -> dialog.dismiss());
        dialog.setCancelable(true);
    }

    private void updateInstallButton(String label, boolean enabled) {
        Button button = findViewById(R.id.mod_detail_install);
        button.setText(label);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.72f);
    }

    private void showCatalog() {
        if (installing) return;
        transition(detail, catalog);
        selectedMod = null;
        selectedVersion = null;
    }

    private void transition(View outgoing, View incoming) {
        outgoing.animate()
                .alpha(0f)
                .translationX(-dp(18))
                .setDuration(160L)
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setTranslationX(0f);
                    incoming.setVisibility(View.VISIBLE);
                    incoming.setAlpha(0f);
                    incoming.setTranslationX(dp(22));
                    incoming.animate().alpha(1f).translationX(0f).setDuration(260L).start();
                })
                .start();
    }

    private void registerPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack);
        }
    }

    private void handleBack() {
        if (installing) return;
        if (detail != null && detail.getVisibility() == View.VISIBLE) {
            showCatalog();
        } else {
            UiMotion.finish(this, root);
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
        }
        if (bytes >= 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return bytes + " B";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
