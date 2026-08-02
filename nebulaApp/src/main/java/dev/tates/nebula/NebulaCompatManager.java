package dev.tates.nebula;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

final class NebulaCompatManager {
    private static final String BASE_URL = "https://api.nebulaau.space/compat/nebulacompat";
    private static final String MANIFEST_URL = BASE_URL + "/manifest";
    private static final String PUBLIC_KEY_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+xKN6laA2CtgXHn8tcDQXlhhawSjwiglX4b7Ko5wku6mW56BZrvnfPhDNIbh+jIF/2zqcl8ufKrOo50qJNHrNA==";
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String STATE_PREFS = "nebula_compat_state";

    private NebulaCompatManager() {}

    static File getInstalledFile(Context context) {
        return new File(new File(context.getFilesDir(), "compat"), "NebulaCompat.dll");
    }

    static synchronized File ensureInstalled(Context context, String sessionToken) throws Exception {
        File installed = getInstalledFile(context);
        if (BuildConfig.DEBUG_MODE && installed.isFile()) {
            Log.i("NebulaCompat", "debugMode=true; preserving the local NebulaCompat.dll");
            return installed;
        }
        SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        String cachedHash = state.getString("sha256", "");
        boolean cachedValid = installed.isFile() && isSha256(cachedHash)
                && installed.length() == state.getLong("size", -1L)
                && cachedHash.equals(sha256(installed));
        long checkedAt = state.getLong("checkedAt", 0L);
        if (cachedValid && System.currentTimeMillis() - checkedAt < CHECK_INTERVAL_MS) return installed;
        if (sessionToken == null || sessionToken.isEmpty()) {
            if (cachedValid) return installed;
            throw new IOException("A confirmed Nebula session is required to download compatibility support");
        }

        Manifest manifest;
        try {
            manifest = fetchManifest(sessionToken);
        } catch (IOException error) {
            if (cachedValid) return installed;
            throw error;
        }
        if (installed.isFile() && installed.length() == manifest.size
                && manifest.sha256.equals(sha256(installed))) {
            saveState(state, manifest);
            return installed;
        }

        File parent = installed.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Could not create private compatibility directory");
        }
        File temporary = new File(parent, "NebulaCompat.dll.download");
        download(sessionToken, manifest, temporary);
        try {
            Files.move(temporary.toPath(), installed.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception atomicMoveError) {
            Files.move(temporary.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        saveState(state, manifest);
        return installed;
    }

    private static Manifest fetchManifest(String sessionToken) throws Exception {
        HttpURLConnection connection = open(MANIFEST_URL, sessionToken, "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw responseError(connection, "Compatibility manifest", status);
            String body = readLimited(connection.getInputStream(), 32 * 1024);
            JSONObject json = new JSONObject(body);
            String version = json.optString("version", "");
            String hash = json.optString("sha256", "").toLowerCase(Locale.US);
            long size = json.optLong("size", -1L);
            String algorithm = json.optString("signatureAlgorithm", "");
            String encodedSignature = json.optString("signature", "");
            if (!version.matches("[A-Za-z0-9._-]{1,80}") || !isSha256(hash)
                    || size <= 0L || size > MAX_BYTES || !"SHA256withECDSA".equals(algorithm)
                    || encodedSignature.isEmpty()) {
                throw new IOException("Compatibility manifest is invalid");
            }
            String payload = version + "\n" + size + "\n" + hash;
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey());
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.decode(encodedSignature, Base64.DEFAULT))) {
                throw new IOException("Compatibility manifest signature is invalid");
            }
            return new Manifest(version, size, hash);
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String sessionToken, Manifest manifest, File outputFile) throws Exception {
        HttpURLConnection connection = open(BASE_URL, sessionToken, "application/octet-stream");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw responseError(connection, "Compatibility download", status);
            if (connection.getContentLengthLong() != manifest.size) {
                throw new IOException("Compatibility download returned an unexpected size");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            try (InputStream input = connection.getInputStream();
                    FileOutputStream output = new FileOutputStream(outputFile, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > manifest.size || total > MAX_BYTES) {
                        throw new IOException("Compatibility download is too large");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            } catch (Exception error) {
                outputFile.delete();
                throw error;
            }
            if (total != manifest.size || !manifest.sha256.equals(hex(digest.digest()))) {
                outputFile.delete();
                throw new IOException("Compatibility download failed its integrity check");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String token, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.1 Android");
        return connection;
    }

    private static IOException responseError(HttpURLConnection connection, String operation, int status) {
        String error = readError(connection.getErrorStream());
        return new IOException(error.isEmpty() ? operation + " failed with HTTP " + status : error);
    }

    private static PublicKey publicKey() throws Exception {
        byte[] encoded = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static void saveState(SharedPreferences state, Manifest manifest) {
        state.edit().putString("version", manifest.version).putString("sha256", manifest.sha256)
                .putLong("size", manifest.size).putLong("checkedAt", System.currentTimeMillis()).apply();
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value));
        return result.toString();
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException("Server response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static String readError(InputStream input) {
        if (input == null) return "";
        try {
            return new JSONObject(readLimited(input, 16 * 1024)).optString("error", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class Manifest {
        final String version;
        final long size;
        final String sha256;

        Manifest(String version, long size, String sha256) {
            this.version = version;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
