package dev.tates.nebula;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NpkgInstaller {
    public static final long MAX_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_UNCOMPRESSED_BYTES = 768L * 1024L * 1024L;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final int MAX_FILES = 256;

    private NpkgInstaller() {}

    public static String getInstalledVersion(Context context, String packageId) {
        try {
            JSONObject record = readInstallRecord(context, packageId);
            return record == null ? null : record.optString("version", null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isManagedSharedFile(Context context, File sharedFile) {
        try {
            String name = sharedFile.getName();
            File recordDir = getRecordDir(context);
            File[] records = recordDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
            if (records == null) return false;
            for (File recordFile : records) {
                JSONObject record = new JSONObject(Utilities.readTextFile(recordFile, MAX_MANIFEST_BYTES));
                JSONArray files = record.optJSONArray("files");
                if (files == null) continue;
                for (int i = 0; i < files.length(); i++) {
                    String destination = files.optString(i, "");
                    if (!destination.startsWith("plugins/")) continue;
                    String relative = destination.substring("plugins/".length());
                    String topLevel = relative.contains("/")
                            ? relative.substring(0, relative.indexOf('/')) : relative;
                    if (name.equals(topLevel)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void uninstall(Context context, String packageId) throws Exception {
        JSONObject record = readInstallRecord(context, packageId);
        if (record == null) throw new IOException("This mod is not installed in the active profile");
        JSONArray files = record.optJSONArray("files");
        if (files == null) throw new IOException("Installation record is missing files");
        Set<String> otherOwnedFiles = otherOwnedDestinations(context, packageId);
        File profileRoot = new File(ProfileManager.getProfilesRoot(context), ProfileManager.getActiveName(context));
        File sharedPlugins = Utilities.getModsDirectory(context);
        for (int i = 0; i < files.length(); i++) {
            String destination = cleanDestination(files.optString(i, ""));
            if (otherOwnedFiles.contains(destination)) continue;
            File profileFile = resolveInside(profileRoot, destination);
            File sharedFile = resolveInside(sharedPlugins,
                    destination.substring("plugins/".length()));
            if (profileFile.exists() && !Utilities.deleteRecursive(profileFile)) {
                throw new IOException("Could not remove " + profileFile.getName());
            }
            if (sharedFile.exists() && !Utilities.deleteRecursive(sharedFile)) {
                throw new IOException("Could not remove active " + sharedFile.getName());
            }
            removeEmptyParents(profileFile.getParentFile(), new File(profileRoot, "plugins"));
            removeEmptyParents(sharedFile.getParentFile(), sharedPlugins);
        }
        File recordFile = new File(getRecordDir(context), safeName(packageId) + ".json");
        if (!recordFile.delete()) throw new IOException("Could not remove the installation record");
        FusionRuntimeManager.modsChanged(context);
    }

    private static JSONObject readInstallRecord(Context context, String packageId) throws Exception {
        File record = new File(getRecordDir(context), safeName(packageId) + ".json");
        if (!record.isFile()) return null;
        JSONObject value = new JSONObject(Utilities.readTextFile(record, MAX_MANIFEST_BYTES));
        if (!packageId.equals(value.optString("id", ""))) {
            throw new IOException("Installation record identity mismatch");
        }
        return value;
    }

    private static File getRecordDir(Context context) {
        File profileRoot = new File(ProfileManager.getProfilesRoot(context), ProfileManager.getActiveName(context));
        return new File(profileRoot, ".nebula/packages");
    }

    private static Set<String> otherOwnedDestinations(Context context, String excludedPackageId) {
        Set<String> destinations = new HashSet<>();
        File[] records = getRecordDir(context).listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (records == null) return destinations;
        for (File recordFile : records) {
            try {
                JSONObject record = new JSONObject(Utilities.readTextFile(recordFile, MAX_MANIFEST_BYTES));
                if (excludedPackageId.equals(record.optString("id", ""))) continue;
                JSONArray files = record.optJSONArray("files");
                if (files == null) continue;
                for (int i = 0; i < files.length(); i++) destinations.add(files.optString(i, ""));
            } catch (Exception ignored) {
            }
        }
        return destinations;
    }

    private static void removeEmptyParents(File directory, File stopAt) throws IOException {
        String stopPath = stopAt.getCanonicalPath();
        File current = directory;
        while (current != null && !current.getCanonicalPath().equals(stopPath)) {
            File[] children = current.listFiles();
            if (children == null || children.length != 0 || !current.delete()) return;
            current = current.getParentFile();
        }
    }

    public interface Progress {
        void update(String phase, long completed, long total);
    }

    public static void downloadAndInstall(Context context, ModCatalogClient.Mod mod,
            ModCatalogClient.Version version, Progress progress) throws Exception {
        File downloads = new File(context.getCacheDir(), "npkg-downloads");
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw new IOException("Could not create package download directory");
        }
        File packageFile = new File(downloads, safeName(mod.id + "-" + version.version) + ".npkg");
        File partial = new File(packageFile.getPath() + ".partial");
        Utilities.deleteRecursive(partial);

        try {
            download(version, partial, progress);
            Utilities.deleteRecursive(packageFile);
            if (!partial.renameTo(packageFile)) {
                Utilities.copyFile(partial, packageFile);
                Utilities.deleteRecursive(partial);
            }
            install(context, packageFile, mod, version, progress);
        } finally {
            Utilities.deleteRecursive(partial);
        }
    }

    private static void download(ModCatalogClient.Version version, File output,
            Progress progress) throws Exception {
        HttpURLConnection connection = ModCatalogClient.open(version.downloadUrl);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.nebula.npkg+zip");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("Package request failed with HTTP " + status);
        }
        long contentLength = connection.getContentLengthLong();
        if (contentLength > 0 && contentLength != version.size) {
            connection.disconnect();
            throw new IOException("Server package size does not match the catalog");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = connection.getInputStream();
             FileOutputStream file = new FileOutputStream(output, false)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_PACKAGE_BYTES || total > version.size) {
                    throw new IOException("Downloaded package exceeds its declared size");
                }
                file.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                if (progress != null) progress.update("Downloading", total, version.size);
            }
        } finally {
            connection.disconnect();
        }
        if (total != version.size) throw new IOException("Package download is incomplete");
        if (!hex(digest.digest()).equals(version.sha256)) {
            throw new IOException("Package SHA-256 does not match the catalog");
        }
    }

    private static void install(Context context, File packageFile, ModCatalogClient.Mod catalogMod,
            ModCatalogClient.Version catalogVersion, Progress progress) throws Exception {
        File stage = new File(context.getCacheDir(), "npkg-stage-" + UUID.randomUUID());
        File transaction = new File(context.getCacheDir(), "npkg-transaction-" + UUID.randomUUID());
        if (!stage.mkdirs() || !transaction.mkdirs()) {
            throw new IOException("Could not create package staging directory");
        }

        List<InstallFile> files = new ArrayList<>();
        File profileRoot = new File(
                ProfileManager.getProfilesRoot(context),
                ProfileManager.getActiveName(context));
        List<File> writtenTargets = new ArrayList<>();
        Map<File, File> backups = new HashMap<>();
        try (ZipFile zip = new ZipFile(packageFile)) {
            JSONObject manifest = readManifest(zip);
            validateManifest(manifest, catalogMod, catalogVersion);
            JSONArray fileItems = manifest.getJSONObject("install").getJSONArray("files");
            if (fileItems.length() == 0 || fileItems.length() > MAX_FILES) {
                throw new IOException("Package contains an invalid number of files");
            }

            Set<String> declaredEntries = new HashSet<>();
            Set<String> destinations = new HashSet<>();
            long totalUncompressed = 0;
            for (int i = 0; i < fileItems.length(); i++) {
                JSONObject item = fileItems.getJSONObject(i);
                String source = cleanArchivePath(required(item, "source"));
                String destination = cleanDestination(required(item, "destination"));
                if (!source.startsWith("payload/") || !declaredEntries.add(source)
                        || !destinations.add(destination.toLowerCase(Locale.US))) {
                    throw new IOException("Package contains duplicate or invalid file mappings");
                }
                long size = item.getLong("size");
                String expectedHash = required(item, "sha256").toLowerCase(Locale.US);
                if (size < 0 || !expectedHash.matches("[0-9a-f]{64}")) {
                    throw new IOException("Package contains invalid file verification data");
                }
                totalUncompressed += size;
                if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Package expands beyond the safety limit");
                }

                ZipEntry entry = zip.getEntry(source);
                if (entry == null || entry.isDirectory() || entry.getSize() != size) {
                    throw new IOException("Package payload is missing or has an invalid size: " + source);
                }
                File stagedFile = new File(stage, "file-" + i);
                extractVerified(zip, entry, stagedFile, size, expectedHash);
                File target = resolveInside(profileRoot, destination);
                files.add(new InstallFile(destination, stagedFile, target));
                if (progress != null) progress.update("Validating", i + 1L, fileItems.length());
            }

            Set<String> allowed = new HashSet<>(declaredEntries);
            allowed.add("manifest.json");
            Set<String> archiveNames = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = cleanArchivePath(entry.getName());
                if (!archiveNames.add(name)) {
                    throw new IOException("Package contains duplicate archive entry: " + name);
                }
                if (!entry.isDirectory() && !allowed.contains(name)) {
                    throw new IOException("Package contains undeclared entry: " + name);
                }
            }

            for (int i = 0; i < files.size(); i++) {
                InstallFile installFile = files.get(i);
                File parent = installFile.target.getParentFile();
                if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                    throw new IOException("Could not create install directory");
                }
                if (installFile.target.exists()) {
                    File backup = new File(transaction, "backup-" + i);
                    Utilities.copyFile(installFile.target, backup);
                    backups.put(installFile.target, backup);
                }
                writtenTargets.add(installFile.target);
                Utilities.copyFile(installFile.staged, installFile.target);
                if (progress != null) progress.update("Installing", i + 1L, files.size());
            }

            File recordFile = installRecordFile(profileRoot, manifest.getString("id"));
            if (recordFile.exists()) {
                File backup = new File(transaction, "backup-record");
                Utilities.copyFile(recordFile, backup);
                backups.put(recordFile, backup);
            }
            writtenTargets.add(recordFile);
            writeInstallRecord(profileRoot, manifest, files);
            ProfileManager.stageActive(context);
        } catch (Throwable error) {
            rollback(writtenTargets, backups);
            try {
                ProfileManager.stageActive(context);
            } catch (Throwable ignored) {
            }
            throw error;
        } finally {
            Utilities.deleteRecursive(stage);
            Utilities.deleteRecursive(transaction);
        }
    }

    private static JSONObject readManifest(ZipFile zip) throws Exception {
        ZipEntry entry = zip.getEntry("manifest.json");
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0
                || entry.getSize() > MAX_MANIFEST_BYTES) {
            throw new IOException("Package manifest is missing or too large");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return new JSONObject(readUtf8(input, MAX_MANIFEST_BYTES));
        }
    }

    private static void validateManifest(JSONObject manifest, ModCatalogClient.Mod mod,
            ModCatalogClient.Version version) throws Exception {
        if (!"nebula-package".equals(required(manifest, "format"))
                || manifest.optInt("formatVersion", -1) != 1) {
            throw new IOException("Unsupported Nebula package format");
        }
        if (!mod.packageId.equals(required(manifest, "id"))
                || !version.version.equals(required(manifest, "version"))) {
            throw new IOException("Package identity does not match the catalog");
        }
        JSONArray licenses = manifest.optJSONArray("licenses");
        if (licenses == null || licenses.length() == 0 || licenses.length() > 32) {
            throw new IOException("Manifest must declare at least one SPDX license");
        }
        Set<String> manifestLicenses = new HashSet<>();
        for (int i = 0; i < licenses.length(); i++) {
            String identifier = licenses.optString(i, "").trim();
            if (!identifier.matches("[A-Za-z0-9.+-]{1,128}")) {
                throw new IOException("Manifest contains an invalid SPDX license identifier");
            }
            manifestLicenses.add(identifier);
        }
        if (!manifestLicenses.equals(new HashSet<>(mod.licenses))) {
            throw new IOException("Package licenses do not match the catalog");
        }
        Map<String, String> manifestRepositories = ModCatalogClient.parseGithubRepos(
                manifest.optJSONArray("githubRepos"));
        if (!manifestRepositories.equals(mod.githubRepos)) {
            throw new IOException("Package GitHub repositories do not match the catalog");
        }
        JSONObject licenseComponents = manifest.optJSONObject("licenseComponents");
        if (licenseComponents != null) {
            for (String identifier : manifestLicenses) {
                JSONArray components = licenseComponents.optJSONArray(identifier);
                if (components == null || components.length() == 0 || components.length() > 64) {
                    throw new IOException("Manifest license is missing component attribution");
                }
                for (int i = 0; i < components.length(); i++) {
                    if (components.optString(i, "").trim().isEmpty()) {
                        throw new IOException("Manifest contains an invalid licensed component");
                    }
                }
            }
        }
        JSONObject platform = manifest.getJSONObject("platform");
        if (!"android".equals(required(platform, "os"))
                || !"arm64-v8a".equals(required(platform, "architecture"))
                || !"fusioncore".equals(required(platform, "runtime"))
                || !GameCompatibility.AMONG_US_PACKAGE.equals(required(platform, "gamePackage"))
                || !GameCompatibility.REQUIRED_VERSION.equals(required(platform, "gameVersion"))) {
            throw new IOException("Package is not compatible with this Android runtime");
        }
        JSONObject install = manifest.getJSONObject("install");
        if (!"activeProfile".equals(required(install, "targetRoot"))) {
            throw new IOException("Unsupported package installation target");
        }
    }

    private static void extractVerified(ZipFile zip, ZipEntry entry, File output,
            long expectedSize, String expectedHash) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = zip.getInputStream(entry);
             FileOutputStream file = new FileOutputStream(output, false)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > expectedSize) throw new IOException("Payload file exceeds declared size");
                file.write(buffer, 0, count);
                digest.update(buffer, 0, count);
            }
        }
        if (total != expectedSize || !hex(digest.digest()).equals(expectedHash)) {
            throw new IOException("Payload verification failed: " + entry.getName());
        }
    }

    private static void writeInstallRecord(File profileRoot, JSONObject manifest,
            List<InstallFile> files) throws Exception {
        JSONObject record = new JSONObject();
        record.put("id", manifest.getString("id"));
        record.put("name", manifest.getString("name"));
        record.put("version", manifest.getString("version"));
        record.put("licenses", manifest.getJSONArray("licenses"));
        if (manifest.has("licenseComponents")) {
            record.put("licenseComponents", manifest.getJSONObject("licenseComponents"));
        }
        if (manifest.has("githubRepos")) {
            record.put("githubRepos", manifest.getJSONArray("githubRepos"));
        }
        record.put("installedAt", System.currentTimeMillis());
        JSONArray destinations = new JSONArray();
        for (InstallFile file : files) destinations.put(file.destination);
        record.put("files", destinations);
        File recordDir = new File(profileRoot, ".nebula/packages");
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            throw new IOException("Could not create package record directory");
        }
        Utilities.writeTextFile(installRecordFile(profileRoot, manifest.getString("id")),
                record.toString(2));
    }

    private static File installRecordFile(File profileRoot, String packageId) {
        return new File(new File(profileRoot, ".nebula/packages"),
                safeName(packageId) + ".json");
    }

    private static void rollback(List<File> targets, Map<File, File> backups) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            File target = targets.get(i);
            Utilities.deleteRecursive(target);
            File backup = backups.get(target);
            if (backup != null) {
                try {
                    Utilities.copyFile(backup, target);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String cleanArchivePath(String value) throws IOException {
        if (value.isEmpty() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.contains("../") || value.contains("/..")
                || value.contains(":")) {
            throw new IOException("Unsafe package path");
        }
        return value;
    }

    private static String cleanDestination(String value) throws IOException {
        String clean = cleanArchivePath(value);
        if (!clean.startsWith("plugins/") || clean.endsWith("/")) {
            throw new IOException("Package destination must be inside plugins");
        }
        return clean;
    }

    private static File resolveInside(File root, String relative) throws IOException {
        File target = new File(root, relative);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!target.getCanonicalPath().startsWith(rootPath)) {
            throw new IOException("Package destination escapes the selected profile");
        }
        return target;
    }

    private static String required(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IOException("Manifest is missing " + key);
        return value;
    }

    private static String readUtf8(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IOException("Manifest exceeds the safety limit");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static String safeName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class InstallFile {
        final String destination;
        final File staged;
        final File target;

        InstallFile(String destination, File staged, File target) {
            this.destination = destination;
            this.staged = staged;
            this.target = target;
        }
    }
}
