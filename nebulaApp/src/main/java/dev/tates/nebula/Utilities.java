package dev.tates.nebula;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utilities {
    private static final String TAG = "Nebula";
    private static final String LAST_ERROR_FILE = "last_error.txt";
    private static final String LAUNCH_SENTINEL_FILE = "launch_in_progress.txt";
    private static final String AUTH_CONFIG_FILE = "nebula-auth.properties";

    public static Method findOnCreateMethod(Class<?> clazz) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod("onCreate", Bundle.class);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        String className = clazz != null ? clazz.getName() : "<unknown>";
        throw new NoSuchMethodException("onCreate(Bundle) not found for " + className);
    }

    public static void applyWindowInsets(View root, int basePadding) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int insetTop;
            int insetBottom;
            int insetLeft;
            int insetRight;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                insetTop = bars.top;
                insetBottom = bars.bottom;
                insetLeft = bars.left;
                insetRight = bars.right;
            } else {
                insetTop = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
                insetLeft = insets.getSystemWindowInsetLeft();
                insetRight = insets.getSystemWindowInsetRight();
            }

            v.setPadding(
                    basePadding + insetLeft,
                    basePadding + insetTop,
                    basePadding + insetRight,
                    basePadding + insetBottom
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    public static String formatVersionText(String versionName, long versionCode) {
        if (versionCode > 0L) {
            return "v" + versionName + " (" + versionCode + ")";
        }
        return "v" + versionName;
    }

    public static boolean extractZipFromAssets(Context context, String assetName, File outputFolder) {
        try {
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputFolder.getAbsolutePath());
            }

            String outputRoot = outputFolder.getCanonicalPath() + File.separator;
            byte[] buffer = new byte[8192];

            try (InputStream is = context.getAssets().open(assetName);
                 ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    String entryName = normalizeZipEntryName(ze.getName());
                    if (entryName == null || entryName.isEmpty()) {
                        zis.closeEntry();
                        continue;
                    }

                    File target = new File(outputFolder, entryName);
                    String targetPath = target.getCanonicalPath();

                    if (!targetPath.startsWith(outputRoot)) {
                        throw new IOException("Blocked zip entry outside output folder: " + entryName);
                    }

                    if (ze.isDirectory()) {
                        if (!target.exists() && !target.mkdirs()) {
                            throw new IOException("Failed to create directory: " + targetPath);
                        }
                    } else {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
                        }

                        try (FileOutputStream fos = new FileOutputStream(target)) {
                            int count;
                            while ((count = zis.read(buffer)) != -1) {
                                fos.write(buffer, 0, count);
                            }
                        }
                    }

                    zis.closeEntry();
                }
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract " + assetName + " from assets!", e);
            return false;
        }
    }

    public static boolean extractSingleZipAssetEntry(Context context, String assetName, String entryName, File outputFile) {
        try (InputStream is = context.getAssets().open(assetName);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String currentEntryName = normalizeZipEntryName(ze.getName());
                String wantedEntryName = normalizeZipEntryName(entryName);
                if (currentEntryName == null || wantedEntryName == null || !wantedEntryName.equals(currentEntryName)) {
                    zis.closeEntry();
                    continue;
                }

                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
                }

                try (FileOutputStream fos = new FileOutputStream(outputFile, false)) {
                    copyStream(zis, fos);
                }
                zis.closeEntry();
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract single entry " + entryName + " from " + assetName, e);
            return false;
        }

        Log.e(TAG, "Entry not found in asset zip: " + entryName);
        return false;
    }

    private static String normalizeZipEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }

        String normalized = entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isEmpty()
                || normalized.equals(".")
                || normalized.startsWith("../")
                || normalized.contains("/../")) {
            return "";
        }

        return normalized;
    }

    public static boolean copyAssetToFile(Context context, String assetPath, File outputFile) {
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
            }

            try (InputStream inputStream = context.getAssets().open(assetPath);
                 OutputStream outputStream = new FileOutputStream(outputFile, false)) {
            copyStream(inputStream, outputStream);
            return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy asset " + assetPath + " to " + outputFile.getAbsolutePath(), e);
            return false;
        }
    }

    public static boolean copyAssets(AssetManager gameAssets, String assetPath, File outputFolder) {
        deleteRecursive(outputFolder);

        try {
            if (copyAssetEntry(gameAssets, assetPath, outputFolder)) {
                Log.i(TAG, "Successfully copied Unity Data assets to: " + outputFolder.getAbsolutePath());
            } else {
                Log.e(TAG, "Could not find Unity Data assets!");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy Unity Data assets!", e);
            return false;
        }

        return true;
    }

    public static boolean copyAssetEntry(AssetManager gameAssets, String assetPath, File outputTarget) throws IOException {
        String[] children = gameAssets.list(assetPath);
        if (children == null) {
            return false;
        }

        if (children.length > 0) {
            if (!outputTarget.exists() && !outputTarget.mkdirs()) {
                return false;
            }

            for (String child : children) {
                File childTarget = new File(outputTarget, child);
                String childPath = assetPath + "/" + child;
                if (!copyAssetEntry(gameAssets, childPath, childTarget)) {
                    return false;
                }
            }
            return true;
        }

        File parent = outputTarget.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        byte[] buffer = new byte[8192];
        try (InputStream is = gameAssets.open(assetPath);
             OutputStream os = new FileOutputStream(outputTarget)) {
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }

        return true;
    }

    public static boolean deleteRecursive(File file) {
        if (file == null) {
            return true;
        }

        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            return file.delete();
        }

        if (!file.exists()) return true;

        if (file.isDirectory()) {
            // Make owned directories writable before descending during cleanup.
            file.setWritable(true, true);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!deleteRecursive(f)) {
                        return false;
                    }
                }
            }
        }

        return file.delete();
    }

    public static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        copyStream(inputStream, outputStream, Long.MAX_VALUE);
    }

    public static long copyStream(InputStream inputStream, OutputStream outputStream,
                                  long maximumBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            total += length;
            if (total > maximumBytes) {
                throw new IOException("Input exceeds the allowed size");
            }
            outputStream.write(buffer, 0, length);
        }
        return total;
    }

    public static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
        }

        try (InputStream inputStream = new FileInputStream(source);
             OutputStream outputStream = new FileOutputStream(target)) {
            copyStream(inputStream, outputStream);
        }
    }

    public static void moveDirectoryContents(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.isDirectory()) {
            throw new IOException("Source is not a directory: " + sourceDir.getAbsolutePath());
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDir.getAbsolutePath());
        }
        File[] files = sourceDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            File target = new File(targetDir, file.getName());
            if (target.exists()) {
                deleteRecursive(target);
            }
            if (!file.renameTo(target)) {
                if (file.isDirectory()) {
                    moveDirectoryContents(file, target);
                    deleteRecursive(file);
                } else {
                    copyFile(file, target);
                    if (!file.delete()) {
                        throw new IOException("Failed to remove copied source file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }

    public static File findFileRecursive(File root, String name, int maxDepth) {
        if (root == null || maxDepth < 0 || !root.exists()) {
            return null;
        }
        if (root.isFile()) {
            return root.getName().equalsIgnoreCase(name) ? root : null;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().equalsIgnoreCase(name)) {
                return file;
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursive(file, name, maxDepth - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static File getLauncherRoot(String packageName) {
        return new File(new File(android.os.Environment.getExternalStorageDirectory(), "NebulaLauncher"), packageName);
    }

    public static File getNebulaRoot(Context context) {
        return new File(context.getFilesDir(), "nebula");
    }

    public static File getRuntimeRoot(Context context) {
        return getPrivateGameRoot(context);
    }

    public static File getRuntimeLogFile(Context context) {
        return new File(new File(getRuntimeRoot(context), "BepInEx"), "LogOutput.log");
    }

    public static File getPrivateRuntimeRoot(Context context) {
        return context.getFilesDir();
    }

    public static File getPrivateGameRoot(Context context) {
        return new File(getPrivateRuntimeRoot(context), "com.innersloth.spacemafia");
    }

    public static File getGameRoot(Context context) {
        return getRuntimeRoot(context);
    }

    public static File getModsDirectory(Context context) {
        return getSharedPluginsDirectory(context);
    }

    public static File getLegacyModsDirectory() {
        return new File(android.os.Environment.getExternalStorageDirectory(), "NebulaLauncher/mods");
    }

    public static File getSharedPluginsDirectory(Context context) {
        return new File(getRuntimeRoot(context), "BepInEx/plugins");
    }

    public static File getPrivatePluginsDirectory(Context context) {
        return new File(getPrivateGameRoot(context), "BepInEx/plugins");
    }

    public static File getProfilesDirectory(Context context) {
        return new File(getNebulaRoot(context), "profiles");
    }

    public static File getPluginsDirectory(Context context, String packageName) {
        return getModsDirectory(context);
    }

    public static File getLastErrorFile(Context context, String packageName) {
        File diagnosticsDirectory = new File(new File(context.getFilesDir(), "diagnostics"), packageName);
        File diagnosticsFile = new File(diagnosticsDirectory, LAST_ERROR_FILE);

        // Older builds kept this beside the emulated game files. Town of Us scans that
        // directory for locale text files and consequently tried to parse last_error.txt
        // as a translation. Preserve an existing report while moving it out of game data.
        File legacyFile = new File(getPrivateTargetDirectory(context, packageName), LAST_ERROR_FILE);
        if (!diagnosticsFile.exists() && legacyFile.isFile()) {
            if (!diagnosticsDirectory.exists() && !diagnosticsDirectory.mkdirs()) {
                Log.w(TAG, "Could not create diagnostics directory for last-error migration");
            } else if (!legacyFile.renameTo(diagnosticsFile)) {
                try (InputStream input = new FileInputStream(legacyFile);
                     OutputStream output = new FileOutputStream(diagnosticsFile, false)) {
                    copyStream(input, output);
                    if (!legacyFile.delete()) {
                        Log.w(TAG, "Copied but could not remove the legacy last-error file");
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Could not migrate the legacy last-error file", e);
                }
            }
        }

        return diagnosticsFile;
    }

    public static File getLaunchSentinelFile(Context context, String packageName) {
        File diagnosticsDirectory = new File(new File(context.getFilesDir(), "diagnostics"), packageName);
        File diagnosticsFile = new File(diagnosticsDirectory, LAUNCH_SENTINEL_FILE);
        File legacyFile = new File(getPrivateTargetDirectory(context, packageName), LAUNCH_SENTINEL_FILE);
        if (!diagnosticsFile.exists() && legacyFile.isFile()) {
            if (!diagnosticsDirectory.exists() && !diagnosticsDirectory.mkdirs()) {
                Log.w(TAG, "Could not create diagnostics directory for launch-sentinel migration");
            } else if (!legacyFile.renameTo(diagnosticsFile)) {
                try {
                    copyFile(legacyFile, diagnosticsFile);
                    if (!legacyFile.delete()) {
                        Log.w(TAG, "Copied but could not remove the legacy launch sentinel");
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Could not migrate the legacy launch sentinel", e);
                }
            }
        }
        return diagnosticsFile;
    }

    public static File getPrivateTargetDirectory(Context context, String packageName) {
        return new File(context.getFilesDir(), packageName);
    }

    public static File getAuthConfigFile(Context context, String packageName) {
        return new File(getPrivateTargetDirectory(context, packageName), AUTH_CONFIG_FILE);
    }

    public static String readTextFile(File file, int maxBytes) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[Math.max(1024, maxBytes)];
            int count = inputStream.read(buffer);
            if (count <= 0) {
                return "";
            }
            return new String(buffer, 0, count, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static String readTailTextFile(File file, int maxBytes) throws IOException {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long length = raf.length();
            int count = (int) Math.min(length, Math.max(1024, maxBytes));
            byte[] buffer = new byte[count];
            raf.seek(Math.max(0L, length - count));
            raf.readFully(buffer);
            return new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static void writeTextFile(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directory: " + parent.getAbsolutePath());
        }

        try (OutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
