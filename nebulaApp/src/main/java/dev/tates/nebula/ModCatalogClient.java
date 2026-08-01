package dev.tates.nebula;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

public final class ModCatalogClient {
    public static final String API_BASE = "https://api.nebulaau.space";
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;

    private ModCatalogClient() {}

    public static List<Mod> fetchCatalog() throws Exception {
        HttpURLConnection connection = open(API_BASE + "/mods");
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Catalog request failed with HTTP " + status);
        }

        JSONObject response;
        try (InputStream input = connection.getInputStream()) {
            response = new JSONObject(readUtf8(input, MAX_CATALOG_BYTES));
        } finally {
            connection.disconnect();
        }

        if (!response.optBoolean("success") || response.optInt("formatVersion", -1) != 1) {
            throw new IOException("The server returned an unsupported catalog");
        }

        JSONArray items = response.optJSONArray("mods");
        if (items == null) throw new IOException("Catalog is missing mods");
        List<Mod> mods = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject platforms = item.optJSONObject("platforms");
            if (platforms != null && !platforms.optBoolean("android", false)) continue;

            String id = required(item, "id");
            String packageId = required(item, "packageId");
            String latestVersion = required(item, "latestVersion");
            JSONArray versionItems = item.getJSONArray("versions");
            List<Version> versions = new ArrayList<>();
            for (int versionIndex = 0; versionIndex < versionItems.length(); versionIndex++) {
                JSONObject version = versionItems.getJSONObject(versionIndex);
                String downloadUrl = required(version, "downloadUrl");
                URL parsedUrl = new URL(downloadUrl);
                if (!"https".equalsIgnoreCase(parsedUrl.getProtocol())
                        || !"api.nebulaau.space".equalsIgnoreCase(parsedUrl.getHost())) {
                    throw new IOException("Catalog contains an untrusted download URL");
                }
                String sha256 = required(version, "sha256").toLowerCase(Locale.ROOT);
                if (!sha256.matches("[0-9a-f]{64}")) {
                    throw new IOException("Catalog contains an invalid package hash");
                }
                long size = version.getLong("size");
                if (size <= 0 || size > NpkgInstaller.MAX_PACKAGE_BYTES) {
                    throw new IOException("Catalog contains an invalid package size");
                }
                versions.add(new Version(
                        required(version, "version"),
                        downloadUrl,
                        size,
                        sha256));
            }
            if (versions.isEmpty()) continue;
            JSONArray licenseItems = item.optJSONArray("licenses");
            if (licenseItems == null || licenseItems.length() == 0) {
                throw new IOException("Catalog entry is missing licenses");
            }
            List<String> licenses = new ArrayList<>();
            for (int licenseIndex = 0; licenseIndex < licenseItems.length(); licenseIndex++) {
                String license = licenseItems.optString(licenseIndex, "").trim();
                if (!license.matches("[A-Za-z0-9.+-]{1,128}")) {
                    throw new IOException("Catalog contains an invalid SPDX license identifier");
                }
                if (!licenses.contains(license)) licenses.add(license);
            }
            Map<String, List<String>> licenseComponents = new LinkedHashMap<>();
            JSONObject componentItems = item.optJSONObject("licenseComponents");
            if (componentItems != null) {
                for (String license : licenses) {
                    JSONArray components = componentItems.optJSONArray(license);
                    if (components == null || components.length() == 0) continue;
                    List<String> names = new ArrayList<>();
                    for (int componentIndex = 0; componentIndex < components.length(); componentIndex++) {
                        String name = components.optString(componentIndex, "").trim();
                        if (!name.isEmpty() && !names.contains(name)) names.add(name);
                    }
                    if (!names.isEmpty()) licenseComponents.put(license, Collections.unmodifiableList(names));
                }
            }
            Map<String, String> githubRepos = parseGithubRepos(item.optJSONArray("githubRepos"));
            mods.add(new Mod(
                    id,
                    packageId,
                    required(item, "name"),
                    item.optString("badge", "M"),
                    item.optString("author", "Unknown author"),
                    item.optString("category", "Mod"),
                    item.optString("description", ""),
                    item.optString("gameVersion", ""),
                    latestVersion,
                    Collections.unmodifiableList(licenses),
                    Collections.unmodifiableMap(licenseComponents),
                    Collections.unmodifiableMap(githubRepos),
                    Collections.unmodifiableList(versions)));
        }
        return Collections.unmodifiableList(mods);
    }

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Nebula-Android/1");
        return connection;
    }

    private static String required(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IOException("Catalog entry is missing " + key);
        return value;
    }

    static Map<String, String> parseGithubRepos(JSONArray items) throws Exception {
        Map<String, String> repositories = new LinkedHashMap<>();
        if (items == null) return repositories;
        if (items.length() > 64) throw new IOException("Too many GitHub repositories");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) throw new IOException("GitHub repository entry must be an object");
            String name = required(item, "name");
            String url = required(item, "url");
            URL parsed = new URL(url);
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"github.com".equalsIgnoreCase(parsed.getHost())
                    || parsed.getUserInfo() != null
                    || parsed.getPort() != -1
                    || parsed.getQuery() != null
                    || parsed.getRef() != null
                    || !parsed.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) {
                throw new IOException("Catalog contains an invalid GitHub repository URL");
            }
            if (repositories.put(name, url) != null) {
                throw new IOException("Catalog contains a duplicate GitHub repository name");
            }
        }
        return repositories;
    }

    private static String readUtf8(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximumBytes) throw new IOException("Server response is too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static final class Mod {
        public final String id;
        public final String packageId;
        public final String name;
        public final String badge;
        public final String author;
        public final String category;
        public final String description;
        public final String gameVersion;
        public final String latestVersion;
        public final List<String> licenses;
        public final Map<String, List<String>> licenseComponents;
        public final Map<String, String> githubRepos;
        public final List<Version> versions;

        Mod(String id, String packageId, String name, String badge, String author,
                String category, String description, String gameVersion,
                String latestVersion, List<String> licenses,
                Map<String, List<String>> licenseComponents,
                Map<String, String> githubRepos, List<Version> versions) {
            this.id = id;
            this.packageId = packageId;
            this.name = name;
            this.badge = badge;
            this.author = author;
            this.category = category;
            this.description = description;
            this.gameVersion = gameVersion;
            this.latestVersion = latestVersion;
            this.licenses = licenses;
            this.licenseComponents = licenseComponents;
            this.githubRepos = githubRepos;
            this.versions = versions;
        }

        public Version latest() {
            for (Version version : versions) {
                if (latestVersion.equals(version.version)) return version;
            }
            return versions.get(0);
        }
    }

    public static final class Version {
        public final String version;
        public final String downloadUrl;
        public final long size;
        public final String sha256;

        Version(String version, String downloadUrl, long size, String sha256) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
