package dev.tates.nebula;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

final class AppUpdateClient {
    private static final String UPDATE_URL = ModCatalogClient.API_BASE + "/updates/app";
    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_JSON_BYTES = 64 * 1024;

    private AppUpdateClient() {}

    static Release check(Context context) throws Exception {
        HttpURLConnection connection = open(UPDATE_URL);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("Update check failed with HTTP " + status);
            JSONObject root;
            try (InputStream input = connection.getInputStream()) {
                root = new JSONObject(readUtf8(input, MAX_JSON_BYTES));
            }
            if (!root.optBoolean("success") || root.optInt("formatVersion", -1) != 1) {
                throw new IOException("Unsupported update response");
            }
            JSONObject app = root.optJSONObject("app");
            if (app == null || !app.optBoolean("available", false)) return null;
            long versionCode = app.getLong("versionCode");
            String versionName = required(app, "versionName");
            String downloadUrl = required(app, "downloadUrl");
            URL parsed = new URL(downloadUrl);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"api.nebulaau.space".equalsIgnoreCase(parsed.getHost())
                    || parsed.getUserInfo() != null || parsed.getPort() != -1) {
                throw new IOException("The API returned an untrusted update URL");
            }
            long size = app.getLong("size");
            if (size <= 0 || size > MAX_APK_BYTES) throw new IOException("Invalid update size");
            String sha256 = required(app, "sha256").toLowerCase(Locale.ROOT);
            if (!sha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid update digest");
            long current = currentVersionCode(context);
            return versionCode > current
                    ? new Release(versionCode, versionName, app.optString("notes", ""), downloadUrl, size, sha256)
                    : null;
        } finally {
            connection.disconnect();
        }
    }

    interface Progress {
        void update(String phase, long completed, long total);
    }

    static File downloadAndVerify(Context context, Release release, Progress progress) throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Could not create update cache");
        File outputFile = new File(directory, "nebula-update.apk");
        HttpURLConnection connection = open(release.downloadUrl);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Update download failed with HTTP " + connection.getResponseCode());
            }
            long declared = connection.getContentLengthLong();
            if (declared != -1 && declared != release.size) throw new IOException("Update size changed");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(outputFile, false)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK_BYTES || total > release.size) throw new IOException("Update is larger than declared");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    if (progress != null) progress.update("Downloading", total, release.size);
                }
                output.getFD().sync();
            }
            if (total != release.size) throw new IOException("Update download was incomplete");
            if (progress != null) progress.update("Verifying download", 0, 0);
            if (!hex(digest.digest()).equals(release.sha256)) throw new IOException("Update integrity check failed");
            verifyApk(context, outputFile, release.versionCode);
            if (progress != null) progress.update("Ready to install", 1, 1);
            return outputFile;
        } catch (Exception error) {
            outputFile.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    private static void verifyApk(Context context, File apk, long expectedVersion) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("Downloaded APK has the wrong package name");
        }
        long candidateVersion = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        if (candidateVersion != expectedVersion || candidateVersion <= currentVersionCode(context)) {
            throw new IOException("Downloaded APK has the wrong version");
        }
        if (!sameSigners(installed, candidate)) throw new IOException("Downloaded APK is signed by a different publisher");
    }

    private static boolean sameSigners(PackageInfo left, PackageInfo right) throws Exception {
        Signature[] a = Build.VERSION.SDK_INT >= 28 ? left.signingInfo.getApkContentsSigners() : left.signatures;
        Signature[] b = Build.VERSION.SDK_INT >= 28 ? right.signingInfo.getApkContentsSigners() : right.signatures;
        if (a == null || b == null || a.length != b.length) return false;
        byte[][] ah = hashes(a); byte[][] bh = hashes(b);
        Arrays.sort(ah, AppUpdateClient::compareBytes);
        Arrays.sort(bh, AppUpdateClient::compareBytes);
        return Arrays.deepEquals(ah, bh);
    }

    private static int compareBytes(byte[] left, byte[] right) {
        int shared = Math.min(left.length, right.length);
        for (int i = 0; i < shared; i++) {
            int comparison = Integer.compare(left[i] & 0xff, right[i] & 0xff);
            if (comparison != 0) return comparison;
        }
        return Integer.compare(left.length, right.length);
    }

    private static byte[][] hashes(Signature[] signatures) throws Exception {
        byte[][] result = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) result[i] = MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray());
        return result;
    }

    private static long currentVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static HttpURLConnection open(String value) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(15_000); connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestMethod("GET"); connection.setRequestProperty("User-Agent", "Nebula-Android/1");
        return connection;
    }

    private static String required(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IOException("Update response is missing " + key);
        return value;
    }

    private static String readUtf8(InputStream input, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int total = 0, count;
        while ((count = input.read(buffer)) != -1) { total += count; if (total > max) throw new IOException("Update response is too large"); output.write(buffer, 0, count); }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(bytes.length * 2); for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value)); return out.toString(); }

    static final class Release {
        final long versionCode; final String versionName; final String notes; final String downloadUrl; final long size; final String sha256;
        Release(long versionCode, String versionName, String notes, String downloadUrl, long size, String sha256) {
            this.versionCode = versionCode; this.versionName = versionName; this.notes = notes; this.downloadUrl = downloadUrl; this.size = size; this.sha256 = sha256;
        }
    }
}
