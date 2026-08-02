package dev.allofus.fusioncore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.tates.nebula.R;
import dev.tates.nebula.GameCompatibility;
import dev.tates.nebula.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;


public class BootstrapActivity extends Activity {

    private static final String TAG = "FusionCore";

    public static final String EXTRA_TARGET_PACKAGE = "target_package";
    public static final String EXTRA_USE_ORIGINAL_LIBUNITY = "og_libunity";
    public static final String BACKUP_UNITY_VERSION = "2017.0.0";
    private static final String GLOBAL_METADATA_FILE = "global-metadata.dat";
    private static final String RUNTIME_MARKER_FILE = ".nebula-runtime.sha256";

    private final AtomicBoolean hookInstalled = new AtomicBoolean(false);
    private final AtomicBoolean fusionInitialized = new AtomicBoolean(false);

    private TextView statusView;
    private TextView progressDetailsView;
    private View spinnerProgress;
    private ProgressBar downloadProgress;
    private volatile PreparedFusionState preparedState;
    private long launchStartedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideSystemBars(this);
        setContentView(R.layout.activity_bootstrap);
        TextView bootstrapTitle = findViewById(R.id.bootstrap_title);
        bootstrapTitle.setText(BuildConfig.DEBUG_MODE ? "NEBULA  DEBUG" : "NEBULA");
        TextView bootstrapVersion = findViewById(R.id.bootstrap_version);
        bootstrapVersion.setText(BuildConfig.VERSION_NAME + "  •  NATIVE ANDROID");
        launchStartedAt = System.currentTimeMillis();
        statusView = findViewById(R.id.bootstrap_status);
        progressDetailsView = findViewById(R.id.bootstrap_progress_details);
        spinnerProgress = findViewById(R.id.bootstrap_progress);
        startSpinner(spinnerProgress);
        downloadProgress = findViewById(R.id.bootstrap_download_progress);
        setPhaseStatus(getString(R.string.bootstrap_status_preparing));

        String targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        if (targetPackage == null || targetPackage.isEmpty()) {
            failAndFinish("No target package specified in intent extras!", null);
            return;
        }

        // Let the loading screen render first, then perform initialization work.
        statusView.post(() -> new Thread(() -> runBootstrapFlow(targetPackage), "bootstrap-flow").start());
    }

    private void runBootstrapFlow(String targetPackage) {
        try {
            android.content.pm.PackageInfo packageInfo =
                    GameCompatibility.getPackageInfo(this, targetPackage);
            if (GameCompatibility.AMONG_US_PACKAGE.equals(targetPackage)
                    && !GameCompatibility.isSupported(packageInfo)) {
                if (!BuildConfig.DEBUG_MODE) {
                    failAndFinish("Nebula requires Among Us package version 2026.6.5 "
                            + "(build 7045); installed Android package version is "
                            + GameCompatibility.getVersionName(packageInfo) + ".", null);
                    return;
                }
                Log.w(TAG, "Debug compatibility override: bootstrapping " + targetPackage
                        + " " + GameCompatibility.getVersionName(packageInfo) + " ("
                        + GameCompatibility.getVersionCode(packageInfo) + ").");
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            failAndFinish("Target package is not installed: " + targetPackage, e);
            return;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent == null) {
            failAndFinish("No launch intent for target package: " + targetPackage, null);
            return;
        }

        ComponentName launcher = launchIntent.getComponent();
        if (launcher == null) {
            launcher = launchIntent.resolveActivity(getPackageManager());
        }

        if (launcher == null) {
            failAndFinish("Failed to resolve launcher activity for target package: " + targetPackage, null);
            return;
        }

        Context gameContext;
        try {
            gameContext = createPackageContext(targetPackage, CONTEXT_IGNORE_SECURITY | CONTEXT_INCLUDE_CODE);
        } catch (Exception e) {
            failAndFinish("Failed to create package context for target package: " + targetPackage, e);
            return;
        }

        boolean useOriginalLibUnity = getIntent().getBooleanExtra(EXTRA_USE_ORIGINAL_LIBUNITY, false);
        try {
            preparedState = prepareFusionState(this, gameContext, targetPackage, useOriginalLibUnity);
        } catch (Throwable t) {
            failAndFinish("Failed while preparing Fusion runtime.", t);
            return;
        }

        setPhaseStatus(getString(R.string.bootstrap_status_installing_hooks));
        try {
            ClassLoaderHooks.installHooks(gameContext.getClassLoader());
            PackageManagerHooks.installHooks(getPackageManager());
            UnityPlayerHooks.installHooks(gameContext);
        } catch (Exception e) {
            Log.e(TAG, "Failed to install base hooks", e);
        }

        final String launcherClassName = launcher.getClassName();
        if (!installLauncherOnCreateHook(gameContext.getClassLoader(), launcherClassName,
                (launcherActivity, bundle) -> {
                    launcherActivity.setRequestedOrientation(
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    hideSystemBars(launcherActivity);
                    initializeFusion(launcherActivity, targetPackage);
                    // This hook runs before the game's onCreate. Queue UI installation so the
                    // Unity activity has completed its initial decor setup first.
                    new Handler(Looper.getMainLooper()).post(
                            () -> attachPersistentLaunchOverlay(launcherActivity));
                })) {
            failAndFinish("Failed to install launcher hook! See log for details.", null);
            return;
        }

        try {
            var launcherClass = gameContext.getClassLoader().loadClass(launcherClassName);

            setPhaseStatus(getString(R.string.bootstrap_status_launching));
            runOnMainThread(() -> {
                try {
                    dev.tates.nebula.Utilities.writeTextFile(
                            dev.tates.nebula.Utilities.getLaunchSentinelFile(this, targetPackage),
                            Long.toString(launchStartedAt));
                    var intent = new Intent(this, launcherClass);
                    startActivity(intent);
                    finish();
                } catch (Throwable t) {
                    failAndFinish("Failed to launch target app's launcher activity: " + launcherClassName, t);
                }
            });
        } catch (Exception e) {
            failAndFinish("Failed to launch target app's launcher activity: " + launcherClassName, e);
        }
    }

    private void setPhaseStatus(String status) {
        runOnMainThread(() -> {
            if (statusView != null) {
                statusView.setText(status);
            }
            if (spinnerProgress != null) {
                spinnerProgress.setVisibility(View.VISIBLE);
            }
            if (downloadProgress != null) {
                downloadProgress.setVisibility(View.GONE);
                downloadProgress.setIndeterminate(false);
                downloadProgress.setProgress(0);
            }
            if (progressDetailsView != null) {
                progressDetailsView.setVisibility(View.GONE);
                progressDetailsView.setText("");
            }
        });
    }

    private void setDownloadStatus(long downloadedBytes, long totalBytes) {
        runOnMainThread(() -> {
            if (spinnerProgress != null) {
                spinnerProgress.setVisibility(View.GONE);
            }
            boolean hasTotal = totalBytes > 0L;
            long progress = hasTotal
                    ? Math.max(0L, Math.min(100L, (downloadedBytes * 100L) / totalBytes))
                    : 0L;
            if (downloadProgress != null) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setIndeterminate(!hasTotal);
                if (hasTotal) {
                    int percent = (int) progress;
                    downloadProgress.setProgress(percent);
                }
            }
            if (statusView != null) {
                statusView.setText(getString(R.string.bootstrap_status_downloading_libunity));
            }
            if (progressDetailsView != null) {
                progressDetailsView.setVisibility(View.VISIBLE);
                int percent = totalBytes > 0L
                        ? (int) progress
                        : 0;
                progressDetailsView.setText(getString(
                        R.string.bootstrap_download_progress,
                        percent,
                        formatBytes(downloadedBytes),
                        totalBytes > 0L ? formatBytes(totalBytes) : "?"
                ));
            }
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex]);
    }

    private void failAndFinish(String message, Throwable error) {
        runOnMainThread(() -> {
            if (error != null) {
                Log.e(TAG, message, error);
            } else {
                Log.e(TAG, message);
            }
            if (statusView != null) {
                statusView.setText(getString(R.string.bootstrap_status_error));
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            runOnUiThread(runnable);
        }
    }

    private interface BeforeOnCreateAction {

        void run(Activity launcherActivity, Bundle bundle);
    }

    private boolean installLauncherOnCreateHook(ClassLoader gameClassLoader,
            String launcherClassName,
            BeforeOnCreateAction action) {
        if (hookInstalled.get()) {
            return true;
        }

        try {
            Class<?> launcherClass = Class.forName(launcherClassName, false, gameClassLoader);
            Method onCreateMethod = Utilities.findOnCreateMethod(launcherClass);
            onCreateMethod.setAccessible(true);

            NebulaHook.hook(onCreateMethod, new NebulaHook.Callback() {
                @Override
                public void beforeCall(NebulaHook.CallFrame callFrame) {
                    if (!(callFrame.thisObject instanceof Activity)) {
                        Log.w(TAG, "Launcher hook hit but receiver is not an Activity: " + callFrame.thisObject);
                        return;
                    }

                    Bundle bundle = null;
                    if (callFrame.args != null && callFrame.args.length > 0 && callFrame.args[0] instanceof Bundle) {
                        bundle = (Bundle) callFrame.args[0];
                    }

                    try {
                        action.run((Activity) callFrame.thisObject, bundle);
                    } catch (Throwable t) {
                        Log.e(TAG, "Fusion pre-onCreate action failed", t);
                    }
                }

            });

            hookInstalled.set(true);
            Log.i(TAG, "Installed launcher onCreate hook for " + launcherClassName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to install launcher onCreate hook for " + launcherClassName, e);
            return false;
        }
    }

    private void attachPersistentLaunchOverlay(Activity gameActivity) {
        gameActivity.runOnUiThread(() -> {
            hideSystemBars(gameActivity);
            ViewGroup decor = (ViewGroup) gameActivity.getWindow().getDecorView();
            View overlay = LayoutInflater.from(gameActivity).inflate(R.layout.activity_bootstrap, decor, false);
            TextView overlayStatus = overlay.findViewById(R.id.bootstrap_status);
            TextView overlayLog = overlay.findViewById(R.id.bootstrap_log);
            ScrollView logContainer = overlay.findViewById(R.id.bootstrap_log_container);
            View icon = overlay.findViewById(R.id.bootstrap_progress);
            View download = overlay.findViewById(R.id.bootstrap_download_progress);
            download.setVisibility(View.GONE);
            overlayStatus.setText("Starting BepInEx and loading " + dev.tates.nebula.ProfileManager.getActiveName(this) + "…");
            startSpinner(icon);

            boolean verbose = getSharedPreferences("nebula_launch", MODE_PRIVATE)
                    .getBoolean("verbose_launch", false);
            if (verbose) {
                logContainer.setVisibility(View.VISIBLE);
                overlayStatus.setText("Verbose launch • " + dev.tates.nebula.ProfileManager.getActiveName(this));
            }
            decor.addView(overlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            Handler handler = new Handler(Looper.getMainLooper());
            final boolean[] interopGenerationMarked = {false};
            Runnable monitor = new Runnable() {
                @Override
                public void run() {
                    if (!overlay.isAttachedToWindow()) return;
                    String tail = readActiveLogTail();
                    if (verbose && !tail.isEmpty()) {
                        overlayLog.setText(tail);
                        logContainer.post(() -> logContainer.fullScroll(View.FOCUS_DOWN));
                    }
                    boolean currentLaunchLog = getActiveLogFile().lastModified() >= launchStartedAt - 1000L;
                    boolean generatingInterop = currentLaunchLog
                            && (tail.contains("Running Cpp2IL")
                            || tail.contains("Generating interop assemblies"));
                    if (generatingInterop) {
                        overlayStatus.setText(
                                "Generating IL2CPP interop, you may encounter a crash");
                        if (!interopGenerationMarked[0]) {
                            interopGenerationMarked[0] = true;
                            getSharedPreferences("nebula_launch", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("il2cpp_interop_generation_in_progress", true)
                                    .commit();
                        }
                    }
                    if (currentLaunchLog && tail.contains("Chainloader startup complete")) {
                        getSharedPreferences("nebula_launch", MODE_PRIVATE)
                                .edit()
                                .remove("il2cpp_interop_generation_in_progress")
                                .apply();
                        File launchSentinel = dev.tates.nebula.Utilities.getLaunchSentinelFile(
                                gameActivity, GameCompatibility.AMONG_US_PACKAGE);
                        if (launchSentinel.exists() && !launchSentinel.delete()) {
                            Log.w(TAG, "Failed to clear successful launch sentinel: "
                                    + launchSentinel.getAbsolutePath());
                        }
                        icon.clearAnimation();
                        decor.removeView(overlay);
                        return;
                    }
                    handler.postDelayed(this, 300L);
                }
            };
            handler.post(monitor);
        });
    }

    private static void hideSystemBars(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void startSpinner(View view) {
        if (view instanceof dev.tates.nebula.NebulaLoaderView) {
            ((dev.tates.nebula.NebulaLoaderView) view).start();
            return;
        }
        android.animation.ObjectAnimator spinner = android.animation.ObjectAnimator.ofFloat(
                view, View.ROTATION, 0f, 360f);
        spinner.setDuration(2200L);
        spinner.setInterpolator(new android.view.animation.LinearInterpolator());
        spinner.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        spinner.start();
    }

    private File getActiveLogFile() {
        return new File(getFilesDir(), "com.innersloth.spacemafia/BepInEx/LogOutput.log");
    }

    private String readActiveLogTail() {
        File log = getActiveLogFile();
        if (!log.isFile()) return "";
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(log, "r")) {
            int count = (int) Math.min(input.length(), 24000L);
            byte[] data = new byte[count];
            input.seek(input.length() - count);
            input.readFully(data);
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private void initializeFusion(Activity launcherActivity, String targetPackage) {
        if (!fusionInitialized.compareAndSet(false, true)) {
            return;
        }

        PreparedFusionState prepared = preparedState;
        if (prepared == null || !targetPackage.equals(prepared.targetPackage)) {
            Log.e(TAG, "Fusion config was not prepared for target package: " + targetPackage);
            return;
        }

        String launcherName = launcherActivity != null
                ? launcherActivity.getClass().getName()
                : "<unknown launcher>";
        Log.i(TAG, "Initializing Fusion for " + targetPackage + " via " + launcherName);

        try {
            FusionConfig config = prepared.config;

            NativeLibraryManager.addFusionLibrary("main");
            NativeLibraryManager.addFusionLibrary("fusion");
            NativeLibraryManager.addDataLibrary("il2cpp");
            NativeLibraryManager.addDataLibrary("unity");
            NativeLibraryManager.setupLibraryHooks(config);

            File stagedConfig = FusionConfigStore.write(this, config);
            Log.i(TAG, "Fusion config staged at " + stagedConfig.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize Fusion in launcher beforeCall", t);
        }
    }

    private PreparedFusionState prepareFusionState(Context appContext,
            Context gameContext,
            String targetPackage,
            boolean useOriginalLibUnity) {
        String gameLibDir = gameContext.getApplicationInfo().nativeLibraryDir;
        String appLibDir = appContext.getApplicationInfo().nativeLibraryDir;
        String targetGameAbi = resolveTargetGameAbi(gameLibDir);
        File appDataDir = new File(appContext.getFilesDir(), targetPackage);
        File dataOnSdCard = appDataDir;

        setPhaseStatus(getString(R.string.bootstrap_status_copy_assets));
        File copiedData = new File(appDataDir, "Data_copy");
        boolean copied = Utilities.copyAssets(gameContext.getAssets(), "bin/Data", copiedData);
        if (!copied) {
            Log.e(TAG, "Failed to copy Unity Data assets! BepInEx may not work correctly.");
        } else {
            applyGlobalMetadataOverride(dataOnSdCard, copiedData);
        }

        setPhaseStatus(getString(R.string.bootstrap_status_detecting_version));
        String version = VersionLookup.TryLookup(copiedData);
        if (version == null) {
            Log.e(TAG, "Failed to determine Unity version! BepInEx may not work correctly.");
            version = BACKUP_UNITY_VERSION;
            useOriginalLibUnity = true;
        } else if (useOriginalLibUnity) {
            Log.i(TAG, "Skipping libunity download");
            useOriginalLibUnity = true;
        } else {
            Log.i(TAG, "Determined Unity version: " + version);
            if (LibUnityDownloader.downloadAndCacheSafely(appDataDir, version, targetGameAbi, new LibUnityDownloader.DownloadProgressListener() {
                @Override
                public void onDownloadStarted(String url, long totalBytes) {
                    setDownloadStatus(0L, totalBytes);
                }

                @Override
                public void onDownloadProgress(long downloadedBytes, long totalBytes) {
                    setDownloadStatus(downloadedBytes, totalBytes);
                }

                @Override
                public void onDownloadFinished(boolean success, boolean usedCache) {
                    // No-op: next phase status is set by prepareFusionState.
                }
            })) {
                Log.i(TAG, "Successfully downloaded libunity for version " + version + " and ABI " + targetGameAbi);
            } else {
                Log.e(TAG, "Failed to download libunity for version " + version + " and ABI " + targetGameAbi + ", falling back to original.");
                useOriginalLibUnity = true;
            }
        }

        setPhaseStatus(getString(R.string.bootstrap_status_extracting_runtime));
        File dotnetDir = new File(appDataDir, "dotnet");

        File bepInExDir = new File(dataOnSdCard, "BepInEx");
        installRuntimeIfNeeded(appContext, "BepInEx-arm64.zip", bepInExDir,
                new File(bepInExDir, "core/BepInEx.Unity.IL2CPP.dll"), true);
        installRuntimeIfNeeded(appContext, "dotnet-arm64.zip", dotnetDir,
                new File(dotnetDir, "System.Private.CoreLib.dll"), false);

        setPhaseStatus(getString(R.string.bootstrap_status_registering_libraries));
        File[] nativeLibs = new File(gameLibDir).listFiles();
        if (nativeLibs != null) {
            for (File file : nativeLibs) {
                String name = file.getName();
                if (name.startsWith("lib") && name.endsWith(".so") && name.length() > 6) {
                    String extractedName = name.substring(3, name.length() - 3);
                    NativeLibraryManager.addGameLibrary(extractedName);
                }
            }
        } else {
            Log.e(TAG, "Failed to list game native libraries! BepInEx may not work correctly.");
        }

        FusionConfig config = new FusionConfig(
                gameLibDir,
                appLibDir,
                appDataDir.getAbsolutePath(),
                bepInExDir.getAbsolutePath(),
                dotnetDir.getAbsolutePath(),
                copiedData.getAbsolutePath(),
                version,
                useOriginalLibUnity
        );

        return new PreparedFusionState(targetPackage, config);
    }

    private static void installRuntimeIfNeeded(Context context,
            String assetName,
            File outputDirectory,
            File requiredFile,
            boolean preserveBepInExConfig) {
        String packagedDigest = digestAsset(context, assetName);
        File marker = new File(outputDirectory, RUNTIME_MARKER_FILE);
        String installedDigest = readOptionalText(marker);

        if (requiredFile.isFile() && packagedDigest.equals(installedDigest)) {
            Log.i(TAG, assetName + " is already installed; skipping extraction.");
            return;
        }

        byte[] preservedConfig = null;
        File configFile = null;
        if (preserveBepInExConfig) {
            configFile = new File(new File(outputDirectory, "config"), "BepInEx.cfg");
            preservedConfig = readOptionalFile(configFile);
        }

        Log.i(TAG, "Installing updated runtime asset " + assetName);
        if (!Utilities.extractZipFromAssets(context, assetName, outputDirectory)
                || !requiredFile.isFile()) {
            throw new IllegalStateException("Failed to install required runtime asset: " + assetName);
        }

        if (preservedConfig != null) {
            writeFile(configFile, preservedConfig);
            Log.i(TAG, "Preserved the existing device BepInEx configuration.");
        }
        writeFile(marker, packagedDigest.getBytes(StandardCharsets.UTF_8));
    }

    private static String digestAsset(Context context, String assetName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = context.getAssets().open(assetName)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to fingerprint runtime asset: " + assetName, e);
        }
    }

    private static String readOptionalText(File file) {
        byte[] contents = readOptionalFile(file);
        return contents == null ? null : new String(contents, StandardCharsets.UTF_8).trim();
    }

    private static byte[] readOptionalFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return java.nio.file.Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to preserve existing file: "
                    + file.getAbsolutePath(), e);
        }
    }

    private static void writeFile(File file, byte[] contents) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create parent directory: "
                    + parent.getAbsolutePath());
        }
        try {
            java.nio.file.Files.write(file.toPath(), contents);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to restore existing file: "
                    + file.getAbsolutePath(), e);
        }
    }

    private void applyGlobalMetadataOverride(File dataOnSdCard, File copiedData) {
        File overrideMetadata = new File(dataOnSdCard, GLOBAL_METADATA_FILE);
        if (!overrideMetadata.isFile()) {
            Log.i(TAG, "No global-metadata override found at " + overrideMetadata.getAbsolutePath());
            return;
        }

        File targetMetadata = new File(
                new File(copiedData, "Managed/Metadata"),
                GLOBAL_METADATA_FILE);
        try {
            copyFile(overrideMetadata, targetMetadata);
            Log.i(TAG, "Applied global-metadata override from " + overrideMetadata.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply global-metadata override from "
                    + overrideMetadata.getAbsolutePath(), e);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
        }

        byte[] buffer = new byte[8192];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
    }

    private static final class PreparedFusionState {

        private final String targetPackage;
        private final FusionConfig config;

        private PreparedFusionState(String targetPackage, FusionConfig config) {
            this.targetPackage = targetPackage;
            this.config = config;
        }
    }

    private String resolveTargetGameAbi(String gameLibDir) {
        if (gameLibDir == null || gameLibDir.isEmpty()) {
            return null;
        }

        String abi = new File(gameLibDir).getName();
        if (abi.isEmpty()) {
            return null;
        }

        return abi;
    }
}

