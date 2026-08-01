package dev.allofus.fusioncore;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LibUnityDownloader {
    private static final String TAG = "FusionCore";
    private static final String LIBUNITY_DOWNLOAD_URL = "https://unity.bepinex.dev/android/";
    private static final String LIBUNITY_CACHE_META_FILE = "libunity.cache.properties";
    private static final long MAX_DOWNLOAD_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 192L * 1024L * 1024L;
    private static final Pattern UNITY_BASE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)");
    private static final String AMONG_US_2026_3_31_LIBUNITY_KEY = "2022.3.44|arm64-v8a";
    private static final String AMONG_US_2026_3_31_LIBUNITY_SHA256 =
            "612a259a2c3a714d6e8b28fa1885cf4002f6d89f6458a75b84a69baa3da68c06";

    public interface DownloadProgressListener {
        void onDownloadStarted(String url, long totalBytes);
        void onDownloadProgress(long downloadedBytes, long totalBytes);
        void onDownloadFinished(boolean success, boolean usedCache);
    }

    public static boolean downloadAndCacheSafely(File outputDir,
                                                 String version,
                                                 String targetGameAbi,
                                                 DownloadProgressListener progressListener) {
        FutureTask<Boolean> task = new FutureTask<>(() -> downloadAndCache(outputDir, version, targetGameAbi, progressListener));
        Thread worker = new Thread(task, "FusionCore-LibUnityDownload");
        worker.start();

        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Libunity download thread was interrupted", e);
            return false;
        } catch (ExecutionException e) {
            Log.e(TAG, "Libunity download failed", e.getCause() != null ? e.getCause() : e);
            return false;
        }
    }

    public static boolean downloadAndCache(File outputDir,
                                           String version,
                                           String targetGameAbi,
                                           DownloadProgressListener progressListener) {
        if (outputDir == null || version == null || version.trim().isEmpty()) {
            Log.e(TAG, "downloadAndCache called with invalid arguments");
            notifyDownloadFinished(progressListener, false, false);
            return false;
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "Failed to create output directory: " + outputDir.getAbsolutePath());
            notifyDownloadFinished(progressListener, false, false);
            return false;
        }

        String currentAbi = normalizeAbiForDownload(targetGameAbi);
        if (currentAbi == null) {
            Log.e(TAG, "Target game ABI is missing or unsupported: " + targetGameAbi);
            notifyDownloadFinished(progressListener, false, false);
            return false;
        }

        File outputLibUnity = new File(outputDir, "libunity.so");
        File tempOutputLibUnity = new File(outputDir, "libunity.so.download");
        File tempZipFile = new File(outputDir, "libunity.so.zip.download");
        File cacheMetaFile = new File(outputDir, LIBUNITY_CACHE_META_FILE);
        String trimmedVersion = version.trim();
        String downloadVersion = normalizeVersionForDownload(trimmedVersion);
        String cacheKey = downloadVersion + "|" + currentAbi;
        String expectedSha256 = expectedSha256(cacheKey);
        if (expectedSha256 == null) {
            Log.e(TAG, "No trusted libunity digest is registered for " + cacheKey);
            notifyDownloadFinished(progressListener, false, false);
            return false;
        }

        if (!trimmedVersion.equals(downloadVersion)) {
            Log.i(TAG, "Normalized Unity version for download URL: " + trimmedVersion + " -> " + downloadVersion);
        }

        if (isCachedLibUnityValid(outputLibUnity, cacheMetaFile, cacheKey, expectedSha256)) {
            Log.i(TAG, "Using cached libunity for " + cacheKey + " at " + outputLibUnity.getAbsolutePath());
            notifyDownloadFinished(progressListener, true, true);
            return true;
        }

        String url = LIBUNITY_DOWNLOAD_URL + downloadVersion + "/" + currentAbi + ".zip";
        Log.i(TAG, "Downloading libunity from " + url);

        HttpURLConnection connection = null;
        boolean extracted = false;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                Log.e(TAG, "Failed to download libunity zip, HTTP " + statusCode);
                notifyDownloadFinished(progressListener, false, false);
                return false;
            }

            long totalBytes = connection.getContentLengthLong();
            if (totalBytes > MAX_DOWNLOAD_BYTES) {
                throw new IOException("libunity archive exceeds the download limit");
            }
            notifyDownloadStarted(progressListener, url, totalBytes);

            byte[] buffer = new byte[8192];
            long downloadedBytes = 0L;
            long lastProgressDispatchMs = 0L;

            try (InputStream is = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream zipOut = new FileOutputStream(tempZipFile, false)) {
                int count;
                while ((count = is.read(buffer)) != -1) {
                    zipOut.write(buffer, 0, count);
                    downloadedBytes += count;
                    if (downloadedBytes > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("libunity archive exceeded the download limit");
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastProgressDispatchMs >= 120L) {
                        notifyDownloadProgress(progressListener, downloadedBytes, totalBytes);
                        lastProgressDispatchMs = now;
                    }
                }
            }

            notifyDownloadProgress(progressListener, downloadedBytes, totalBytes);

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(tempZipFile)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }

                    String entryName = entry.getName();
                    String fileName = entryName == null ? "" : new File(entryName).getName();
                    if (!"libunity.so".equals(fileName)) {
                        zis.closeEntry();
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(tempOutputLibUnity, false)) {
                        int count;
                        long extractedBytes = 0L;
                        while ((count = zis.read(buffer)) != -1) {
                            extractedBytes += count;
                            if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                throw new IOException("libunity exceeded the extraction limit");
                            }
                            fos.write(buffer, 0, count);
                        }
                    }

                    extracted = true;
                    zis.closeEntry();
                    break;
                }
            }

            if (!extracted) {
                Log.e(TAG, "Downloaded zip did not contain libunity.so");
                notifyDownloadFinished(progressListener, false, false);
                return false;
            }

            if (!matchesSha256(tempOutputLibUnity, expectedSha256)) {
                throw new IOException("Downloaded libunity failed authenticity verification");
            }

            if (outputLibUnity.exists() && !outputLibUnity.delete()) {
                Log.e(TAG, "Failed to replace existing libunity: " + outputLibUnity.getAbsolutePath());
                notifyDownloadFinished(progressListener, false, false);
                return false;
            }

            if (!tempOutputLibUnity.renameTo(outputLibUnity)) {
                Log.e(TAG, "Failed to move downloaded libunity into place");
                notifyDownloadFinished(progressListener, false, false);
                return false;
            }

            if (!writeLibUnityCacheMeta(
                    cacheMetaFile, cacheKey, outputLibUnity.length(), expectedSha256)) {
                Log.w(TAG, "Downloaded libunity but failed to update cache metadata");
            }

            Log.i(TAG, "Successfully downloaded libunity to " + outputLibUnity.getAbsolutePath());
            notifyDownloadFinished(progressListener, true, false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download libunity", e);
            notifyDownloadFinished(progressListener, false, false);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempZipFile.exists() && !tempZipFile.delete()) {
                Log.w(TAG, "Failed to clean temporary zip file: " + tempZipFile.getAbsolutePath());
            }
            if (tempOutputLibUnity.exists() && !outputLibUnity.exists() && !tempOutputLibUnity.delete()) {
                Log.w(TAG, "Failed to clean temporary libunity file: " + tempOutputLibUnity.getAbsolutePath());
            }
        }
    }

    private static void notifyDownloadStarted(DownloadProgressListener listener, String url, long totalBytes) {
        if (listener != null) {
            listener.onDownloadStarted(url, totalBytes);
        }
    }

    private static void notifyDownloadProgress(DownloadProgressListener listener, long downloadedBytes, long totalBytes) {
        if (listener != null) {
            listener.onDownloadProgress(downloadedBytes, totalBytes);
        }
    }

    private static void notifyDownloadFinished(DownloadProgressListener listener, boolean success, boolean usedCache) {
        if (listener != null) {
            listener.onDownloadFinished(success, usedCache);
        }
    }

    private static boolean isCachedLibUnityValid(File outputLibUnity, File cacheMetaFile,
                                                 String expectedCacheKey, String expectedSha256) {
        if (!outputLibUnity.exists() || !outputLibUnity.isFile() || outputLibUnity.length() <= 0) {
            return false;
        }
        if (!cacheMetaFile.exists() || !cacheMetaFile.isFile()) {
            return false;
        }

        Properties meta = new Properties();
        try (FileInputStream fis = new FileInputStream(cacheMetaFile)) {
            meta.load(fis);
        } catch (IOException e) {
            Log.w(TAG, "Failed reading libunity cache metadata", e);
            return false;
        }

        String actualKey = meta.getProperty("cacheKey", "");
        if (!expectedCacheKey.equals(actualKey)) {
            return false;
        }

        String sizeString = meta.getProperty("libunitySize", "0");
        try {
            long expectedSize = Long.parseLong(sizeString);
            return expectedSize > 0
                    && expectedSize == outputLibUnity.length()
                    && expectedSha256.equals(meta.getProperty("sha256", ""))
                    && matchesSha256(outputLibUnity, expectedSha256);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid libunity cache metadata size", e);
            return false;
        } catch (IOException e) {
            Log.w(TAG, "Failed to verify cached libunity", e);
            return false;
        }
    }

    private static boolean writeLibUnityCacheMeta(File cacheMetaFile, String cacheKey,
                                                  long libunitySize, String sha256) {
        Properties meta = new Properties();
        meta.setProperty("cacheKey", cacheKey);
        meta.setProperty("libunitySize", Long.toString(libunitySize));
        meta.setProperty("sha256", sha256);

        try (FileOutputStream fos = new FileOutputStream(cacheMetaFile, false)) {
            meta.store(fos, "libunity cache metadata");
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed writing libunity cache metadata", e);
            return false;
        }
    }

    static String expectedSha256(String cacheKey) {
        if (AMONG_US_2026_3_31_LIBUNITY_KEY.equals(cacheKey)) {
            return AMONG_US_2026_3_31_LIBUNITY_SHA256;
        }
        return null;
    }

    static boolean matchesSha256(File file, String expectedSha256) throws IOException {
        return expectedSha256 != null && expectedSha256.equals(sha256(file));
    }

    private static String sha256(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private static String normalizeVersionForDownload(String version) {
        Matcher matcher = UNITY_BASE_VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return version;
    }

    private static String normalizeAbiForDownload(String abiValue) {
        if (abiValue == null) {
            return null;
        }

        String normalized = abiValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash < normalized.length() - 1) {
            normalized = normalized.substring(slash + 1);
        }

        int backslash = normalized.lastIndexOf('\\');
        if (backslash >= 0 && backslash < normalized.length() - 1) {
            normalized = normalized.substring(backslash + 1);
        }

        switch (normalized) {
            case "arm64":
            case "aarch64":
            case "arm64-v8a":
                return "arm64-v8a";
            case "armeabi-v7a":
            case "armeabi":
            case "armv7":
                return "armeabi-v7a";
/*            case "x86":
                return "x86";
            case "x86_64":
                return "x86_64"; */
        }

        return null;
    }
}

