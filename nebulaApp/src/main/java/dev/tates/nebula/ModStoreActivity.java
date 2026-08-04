package dev.tates.nebula;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModStoreActivity extends Activity {
    public static final String EXTRA_MOD_ID = "mod_id";
    private static final String TAG = "NebulaModStore";
    private static final int REQUEST_UPLOAD_DLL = 3101;
    private static final long MAX_CUSTOM_DLL_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ICON_BYTES = 2 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private View root;
    private View catalog;
    private View detail;
    private ModCatalogClient.Mod selectedMod;
    private ModCatalogClient.Version selectedVersion;
    private volatile boolean installing;
    private String requestedModId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestedModId = getIntent().getStringExtra(EXTRA_MOD_ID);
        setContentView(R.layout.activity_mod_store);
        root = findViewById(R.id.mod_store_root);
        catalog = findViewById(R.id.mod_store_catalog);
        detail = findViewById(R.id.mod_store_detail);
        Utilities.applyWindowInsets(root, 0);

        findViewById(R.id.mod_store_back).setOnClickListener(view -> UiMotion.finish(this, root));
        Button uploadDll = findViewById(R.id.mod_store_upload_dll);
        uploadDll.setVisibility(View.VISIBLE);
        uploadDll.setOnClickListener(view -> chooseCustomDll());
        UiMotion.press(uploadDll);
        findViewById(R.id.mod_detail_back).setOnClickListener(view -> showCatalog());
        findViewById(R.id.mod_detail_install).setOnClickListener(view -> {
            String installedVersion = selectedMod == null ? null
                    : NpkgInstaller.getInstalledVersion(this, selectedMod.packageId);
            if (selectedVersion != null && selectedVersion.version.equals(installedVersion)) {
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

    private void chooseCustomDll() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream", "application/x-msdownload", "*/*"
        });
        startActivityForResult(intent, REQUEST_UPLOAD_DLL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_UPLOAD_DLL || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        worker.execute(() -> importCustomDll(uri));
    }

    private void importCustomDll(Uri uri) {
        String fileName = resolveDisplayName(uri);
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".dll")) {
            showImportResult("Choose a .dll file.", false);
            return;
        }
        fileName = sanitizeDllName(fileName);
        if ("NebulaCompat.dll".equalsIgnoreCase(fileName)) {
            showImportResult("NebulaCompat cannot be replaced here.", false);
            return;
        }

        File profilePlugins = ProfileManager.getActivePlugins(this);
        File target = new File(profilePlugins, fileName);
        File sharedTarget = new File(Utilities.getModsDirectory(this), fileName);
        if (NpkgInstaller.isManagedSharedFile(this, sharedTarget)) {
            showImportResult("Uninstall the Mod Store version before replacing this DLL.", false);
            return;
        }
        File temporary = new File(profilePlugins, "." + fileName + ".importing");
        try {
            if (!profilePlugins.isDirectory() && !profilePlugins.mkdirs()) {
                throw new IOException("Could not prepare the active profile.");
            }
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary, false)) {
                if (input == null) throw new IOException("Could not open the selected DLL.");
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_CUSTOM_DLL_BYTES) {
                        throw new IOException("The selected DLL is larger than 128 MB.");
                    }
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Could not replace the existing DLL.");
            }
            if (!temporary.renameTo(target)) {
                Utilities.copyFile(temporary, target);
                if (!temporary.delete()) Log.w(TAG, "Could not remove temporary DLL");
            }
            ProfileManager.stageActive(this);
            FusionRuntimeManager.modsChanged(this);
            showImportResult(fileName + " was added to "
                    + ProfileManager.getActiveName(this) + ".", true);
        } catch (Exception error) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove partial DLL import", error);
            }
            Log.e(TAG, "Custom DLL import failed", error);
            showImportResult(error.getMessage() == null
                    ? "Could not import the selected DLL." : error.getMessage(), false);
        }
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception error) {
            Log.w(TAG, "Could not read selected DLL name", error);
        }
        return uri.getLastPathSegment();
    }

    private String sanitizeDllName(String value) {
        String name = value.replace('\\', '_').replace('/', '_')
                .replaceAll("[^A-Za-z0-9._ -]", "_");
        while (name.startsWith(".")) name = name.substring(1);
        if (name.length() > 120) name = name.substring(name.length() - 120);
        return name;
    }

    private void showImportResult(String message, boolean success) {
        runOnUiThread(() -> NebulaToast.makeText(this, message,
                success ? android.widget.Toast.LENGTH_SHORT : android.widget.Toast.LENGTH_LONG).show());
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
        if (requestedModId != null) {
            for (ModCatalogClient.Mod mod : mods) {
                if (requestedModId.equals(mod.id)) {
                    requestedModId = null;
                    showDetail(mod);
                    break;
                }
            }
        }
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

        FrameLayout badgeHost = new FrameLayout(this);
        TextView badge = text(mod.badge, 18, Color.WHITE, true);
        LanguageManager.skip(badge);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_nebula_icon);
        badgeHost.addView(badge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackgroundResource(R.drawable.bg_nebula_icon);
        icon.setClipToOutline(true);
        icon.setVisibility(View.GONE);
        badgeHost.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        heading.addView(badgeHost, new LinearLayout.LayoutParams(dp(54), dp(54)));
        loadModImage(mod.imageUrl, icon, badge);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        heading.addView(labels, labelsParams);
        TextView modName = text(mod.name, 20, 0xFFF0F3FF, true);
        LanguageManager.skip(modName);
        labels.addView(modName);
        TextView author = text(LanguageManager.translate(this, "by ") + " " + mod.author,
                13, 0xFF8790B9, false);
        LanguageManager.skip(author);
        author.setPadding(0, dp(3), 0, 0);
        labels.addView(author);

        TextView category = text(mod.category, 14, 0xFF63E6FF, false);
        category.setPadding(0, dp(16), 0, dp(16));
        card.addView(category);

        Button install = new Button(this);
        String installedVersion = NpkgInstaller.getInstalledVersion(this, mod.packageId);
        install.setText(installedVersion == null
                ? "Install latest  \u2022  " + mod.latest().version
                : mod.latest().version.equals(installedVersion)
                    ? "Uninstall  \u2022  " + installedVersion
                    : "Update  \u2022  " + installedVersion + " \u2192 " + mod.latest().version);
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
            if (selectedVersion.version.equals(
                    NpkgInstaller.getInstalledVersion(this, mod.packageId))) {
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
        ImageView detailImage = findViewById(R.id.mod_detail_image);
        detailImage.setImageDrawable(null);
        detailImage.setVisibility(View.GONE);
        loadModImage(mod.imageUrl, detailImage, findViewById(R.id.mod_detail_badge));
        ((TextView) findViewById(R.id.mod_detail_title)).setText(mod.name);
        LanguageManager.skip(findViewById(R.id.mod_detail_title));
        TextView detailAuthor = findViewById(R.id.mod_detail_author);
        detailAuthor.setText(LanguageManager.translate(this, "by ") + " " + mod.author + "  \u2022  "
                + LanguageManager.translate(this, "Android compatible"));
        LanguageManager.skip(detailAuthor);
        ((TextView) findViewById(R.id.mod_detail_description)).setText(mod.description);

        RadioGroup versions = findViewById(R.id.mod_detail_versions);
        versions.removeAllViews();
        for (int i = 0; i < mod.versions.size(); i++) {
            ModCatalogClient.Version version = mod.versions.get(i);
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(version);
            option.setText((version == mod.latest() ? "Latest  \u2022  " : "Version  ")
                    + version.version + "  \u2022  " + formatBytes(version.size)
                    + "  \u2022  Among Us " + version.gameVersion
                    + " (" + version.gameVersionCode + ")");
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
        if (installedVersion == null) {
            updateInstallButton("Install " + selectedMod.name, true);
        } else if (selectedVersion != null && selectedVersion.version.equals(installedVersion)) {
            updateInstallButton("Uninstall " + selectedMod.name + "  \u2022  " + installedVersion, true);
        } else {
            updateInstallButton("Install " + selectedMod.name + "  \u2022  "
                    + selectedVersion.version, true);
        }
    }

    private void loadModImage(String imageUrl, ImageView target, View fallback) {
        target.setTag(imageUrl);
        worker.execute(() -> {
            try {
                Bitmap bitmap = downloadIcon(imageUrl);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !imageUrl.equals(target.getTag())) return;
                    target.setImageBitmap(bitmap);
                    target.setVisibility(View.VISIBLE);
                    fallback.setVisibility(View.GONE);
                });
            } catch (Exception ignored) {
                // Initials remain visible when the remote icon is unavailable.
            }
        });
    }

    private Bitmap downloadIcon(String imageUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(true);
        connection.setRequestProperty("User-Agent", "Nebula-Android/1");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Icon request failed");
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_ICON_BYTES) throw new IOException("Icon is too large");
            byte[] encoded;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ICON_BYTES) throw new IOException("Icon is too large");
                    output.write(buffer, 0, count);
                }
                encoded = output.toByteArray();
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1
                    || bounds.outWidth > 2048 || bounds.outHeight > 2048
                    || (long) bounds.outWidth * bounds.outHeight > 4_194_304L) {
                throw new IOException("Icon dimensions are invalid");
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
            if (bitmap == null) throw new IOException("Icon could not be decoded");
            return bitmap;
        } finally {
            connection.disconnect();
        }
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
                    NebulaToast.makeText(this, mod.name + " was uninstalled.",
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
