package dev.tates.nebula;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ProfileManager {
    private static final String PREFS = "nebula_profiles";
    private static final String ACTIVE = "active_profile";
    private static final String DEFAULT_PROFILE = "Default";
    private ProfileManager() {}

    public static File getProfilesRoot(Context context) {
        return new File(context.getFilesDir(), "profiles");
    }

    public static String getActiveName(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(ACTIVE, DEFAULT_PROFILE);
        return sanitizeName(value);
    }

    public static File getActivePlugins(Context context) {
        return getPlugins(context, getActiveName(context));
    }

    public static File getPlugins(Context context, String profileName) {
        return new File(new File(getProfilesRoot(context), sanitizeName(profileName)), "plugins");
    }

    public static List<String> list(Context context) {
        ensureDefault(context);
        File[] entries = getProfilesRoot(context).listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        if (entries != null) {
            Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File entry : entries) names.add(entry.getName());
        }
        return names;
    }

    public static boolean create(Context context, String requestedName) {
        String name = sanitizeName(requestedName);
        if (name.isEmpty() || new File(getProfilesRoot(context), name).exists()) return false;
        return getPlugins(context, name).mkdirs();
    }

    public static void activate(Context context, String profileName) throws IOException {
        String name = sanitizeName(profileName);
        File plugins = getPlugins(context, name);
        if (!plugins.isDirectory() && !plugins.mkdirs()) throw new IOException("Could not create profile");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE, name).apply();
        stageActive(context);
    }

    public static boolean delete(Context context, String profileName) throws IOException {
        String name = sanitizeName(profileName);
        if (DEFAULT_PROFILE.equals(name)) return false;
        boolean removed = Utilities.deleteRecursive(new File(getProfilesRoot(context), name));
        if (name.equals(getActiveName(context))) activate(context, DEFAULT_PROFILE);
        return removed;
    }

    public static void stageActive(Context context) throws IOException {
        ensureDefault(context);
        installCompatibilityPlugin(context);
        File target = Utilities.getSharedPluginsDirectory(context);
        if (!Utilities.deleteRecursive(target) || (!target.exists() && !target.mkdirs())) {
            throw new IOException("Could not prepare FusionCore plugin directory");
        }
        File activePlugins = getActivePlugins(context);
        copyDirectoryContents(activePlugins, target);
    }

    private static void installCompatibilityPlugin(Context context) throws IOException {
        File plugins = getActivePlugins(context);
        if (!plugins.isDirectory() && !plugins.mkdirs()) {
            throw new IOException("Could not create active profile");
        }
        File compatibilityPlugin = new File(plugins, "NebulaCompat.dll");
        File downloaded = NebulaCompatManager.getInstalledFile(context);
        if (!downloaded.isFile()) {
            throw new IOException("NebulaCompat has not been downloaded for this account");
        }
        try {
            Utilities.copyFile(downloaded, compatibilityPlugin);
        } catch (IOException error) {
            throw new IOException("Could not update NebulaCompat.dll");
        }
    }

    private static void ensureDefault(Context context) {
        getPlugins(context, DEFAULT_PROFILE).mkdirs();
    }

    private static void copyDirectoryContents(File source, File target) throws IOException {
        File[] files = source.listFiles();
        if (files == null) return;
        for (File sourceFile : files) {
            File targetFile = new File(target, sourceFile.getName());
            if (sourceFile.isDirectory()) {
                if (!targetFile.exists() && !targetFile.mkdirs()) throw new IOException("Could not create " + targetFile);
                copyDirectoryContents(sourceFile, targetFile);
            } else {
                Utilities.copyFile(sourceFile, targetFile);
            }
        }
    }

    private static String sanitizeName(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
        cleaned = cleaned.replaceAll("\\.{2,}", ".");
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40).trim();
        return cleaned.isEmpty() ? DEFAULT_PROFILE : cleaned;
    }
}
