package dev.tates.nebula;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Small frontend bridge to FusionCore's shared Android runtime layout.
 * FusionCore owns extraction and loading; Nebula only manages user-facing files.
 */
public final class FusionRuntimeManager {
    private static final String REGION_INSTALL_ASSET = "compat/at.duikbo.regioninstall.cfg";
    private static final String EMPTY_REGION_LIST =
            "Regions = {\\\"CurrentRegionIdx\\\":0,\\\"Regions\\\":[]}";

    private FusionRuntimeManager() {}

    public static void ensureModDirectories(Context context) throws IOException {
        File plugins = Utilities.getSharedPluginsDirectory(context);
        if (!plugins.isDirectory() && !plugins.mkdirs()) {
            throw new IOException("Unable to create FusionCore plugins directory: " + plugins);
        }
        ensureRegionInstallConfig(context, plugins);
    }

    public static void modsChanged(Context context) {
        // FusionCore reads the shared BepInEx tree on every native game launch.
    }

    public static void reinstallBepInEx(Context context) throws IOException {
        File bepInEx = new File(Utilities.getRuntimeRoot(context), "BepInEx");
        File expected = Utilities.getSharedPluginsDirectory(context).getParentFile();
        if (expected == null || !bepInEx.getCanonicalFile().equals(expected.getCanonicalFile())) {
            throw new IOException("Refusing to reinstall an unexpected BepInEx directory");
        }
        if (bepInEx.exists() && !Utilities.deleteRecursive(bepInEx)) {
            throw new IOException("Could not remove the existing BepInEx installation");
        }

        context.getSharedPreferences("nebula_launch", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("bepinex_regeneration_pending", true)
                .remove("automatic_bootstrap_retry_active")
                .commit();

        // Restore only profile-owned plugins. Bootstrap will unpack a pristine runtime
        // on the next launch because the runtime digest marker was removed with BepInEx.
        ensureModDirectories(context);
        ProfileManager.stageActive(context);
    }

    private static void ensureRegionInstallConfig(Context context, File plugins) throws IOException {
        File bepInEx = plugins.getParentFile();
        if (bepInEx == null) {
            throw new IOException("Unable to resolve the shared BepInEx directory");
        }

        File configDirectory = new File(bepInEx, "config");
        if (!configDirectory.isDirectory() && !configDirectory.mkdirs()) {
            throw new IOException("Unable to create FusionCore config directory: " + configDirectory);
        }

        File config = new File(configDirectory, "at.duikbo.regioninstall.cfg");
        if (config.isFile() && !isUntouchedEmptyRegionConfig(config)) {
            return;
        }
        if (!Utilities.copyAssetToFile(context, REGION_INSTALL_ASSET, config)) {
            throw new IOException("Unable to seed Mini.RegionInstall configuration: " + config);
        }
    }

    private static boolean isUntouchedEmptyRegionConfig(File config) throws IOException {
        if (config.length() > 64 * 1024) {
            return false;
        }
        byte[] bytes = new byte[(int) config.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(config)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
        }
        String contents = new String(bytes, 0, offset, StandardCharsets.UTF_8);
        for (String line : contents.split("\\R")) {
            if (line.trim().equals(EMPTY_REGION_LIST)) {
                return true;
            }
        }
        return false;
    }
}
