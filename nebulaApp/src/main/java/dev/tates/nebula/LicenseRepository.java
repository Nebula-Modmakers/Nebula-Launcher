package dev.tates.nebula;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LicenseRepository {
    private static final int MAX_LICENSE_BYTES = 1024 * 1024;

    private LicenseRepository() {}

    static List<Usage> installedLicenseUsage(Context context) throws Exception {
        Map<String, Map<String, String>> componentsByLicense = new LinkedHashMap<>();
        addComponent(componentsByLicense, "GPL-3.0-only", "Nebula Launcher",
                null);
        addComponent(componentsByLicense, "GPL-3.0-only", "FusionCore",
                "https://github.com/All-Of-Us-Mods/FusionCore");
        addComponent(componentsByLicense, "MIT", ".NET/CoreCLR 10.0 runtime",
                "https://github.com/dotnet/runtime");
        addComponent(componentsByLicense, "MIT", "HarmonyX 2.16.0",
                "https://github.com/BepInEx/HarmonyX");
        addComponent(componentsByLicense, "MIT", "AsmResolver 6.0.0-beta.5",
                "https://github.com/Washi1337/AsmResolver");
        addComponent(componentsByLicense, "MIT", "AssetRipper.CIL 1.2.2",
                "https://github.com/AssetRipper/AssetRipper.CIL");
        addComponent(componentsByLicense, "MIT", "AssetRipper.Primitives 3.2.0",
                "https://github.com/AssetRipper/AssetRipper.Primitives");
        addComponent(componentsByLicense, "MIT", "Cpp2IL family 2022.1",
                "https://github.com/SamboyCoding/Cpp2IL");
        addComponent(componentsByLicense, "MIT", "Gee.External.Capstone 2.3.2",
                "https://github.com/ds5678/Capstone.NET");
        addComponent(componentsByLicense, "MIT", "Iced 1.21.0",
                "https://github.com/icedland/iced");
        addComponent(componentsByLicense, "MIT", "Mono.Cecil 0.11.6",
                "https://github.com/jbevain/cecil");
        addComponent(componentsByLicense, "MIT", "MonoMod runtime family",
                "https://github.com/MonoMod/MonoMod");
        addComponent(componentsByLicense, "MIT", "SemanticVersioning 2.0.2",
                "https://github.com/adamreeve/semver.net");
        addComponent(componentsByLicense, "MIT", "xDL 2.3.0",
                "https://github.com/hexhacking/xDL");
        addComponent(componentsByLicense, "Apache-2.0", "AndroidX libraries",
                "https://github.com/androidx/androidx");
        addComponent(componentsByLicense, "Apache-2.0", "AndroidHiddenApiBypass 6.1",
                "https://github.com/LSPosed/AndroidHiddenApiBypass");
        addComponent(componentsByLicense, "Apache-2.0", "parallel-hashmap 0cd57d2",
                "https://github.com/greg7mdp/parallel-hashmap");
        addComponent(componentsByLicense, "Apache-2.0 WITH LLVM-exception", "LLVM libc++ (Android NDK 29)",
                "https://github.com/llvm/llvm-project/tree/main/libcxx");
        addComponent(componentsByLicense, "Apache-2.0", "Firebase Android SDK",
                "https://github.com/firebase/firebase-android-sdk");
        addComponent(componentsByLicense, "Apache-2.0", "Google Identity libraries",
                "https://github.com/android/identity-samples");
        addComponent(componentsByLicense, "Apache-2.0", "Kotlin standard library",
                "https://github.com/JetBrains/kotlin");
        addComponent(componentsByLicense, "Apache-2.0", "Material Components for Android",
                "https://github.com/material-components/material-components-android");
        addComponent(componentsByLicense, "Apache-2.0", "Apache Commons Compress 1.20",
                "https://github.com/apache/commons-compress");
        addComponent(componentsByLicense, "BSD-2-Clause", "zstd-jni 1.5.2-3",
                "https://github.com/luben/zstd-jni");
        addComponent(componentsByLicense, "OFL-1.1", "Orbitron font",
                "https://github.com/theleagueof/orbitron");
        addComponent(componentsByLicense, "LicenseRef-Public-Domain", "XZ for Java 1.7",
                "https://github.com/tukaani-project/xz-java");
        addComponent(componentsByLicense, "LGPL-3.0-only", "LSPlant 84256d4",
                "https://github.com/LSPosed/LSPlant");
        addComponent(componentsByLicense, "LGPL-3.0-only", "DexBuilder ac7fb22",
                "https://github.com/LSPosed/DexBuilder");
        addComponent(componentsByLicense, "Apache-2.0", "OpenSSL 3.3.1",
                "https://github.com/openssl/openssl");
        addComponent(componentsByLicense, "Apache-2.0", "Dobby b0176de",
                "https://github.com/jmpews/Dobby");
        addComponent(componentsByLicense, "LGPL-2.1-only", "BepInEx Fusion 6.0.0-fusion.dev",
                "https://github.com/All-Of-Us-Mods/BepInExFusion");
        addComponent(componentsByLicense, "LGPL-3.0-only", "Il2CppInterop 1.5.2-ci.star.12",
                "https://github.com/BepInEx/Il2CppInterop");
        Map<String, ModCatalogClient.Mod> catalogByPackage = new LinkedHashMap<>();
        try {
            for (ModCatalogClient.Mod mod : ModCatalogClient.fetchCatalog()) {
                catalogByPackage.put(mod.packageId, mod);
            }
        } catch (Exception ignored) {
            // New installation records are self-contained and remain useful offline.
        }

        for (String profile : ProfileManager.list(context)) {
            File recordDir = new File(new File(ProfileManager.getProfilesRoot(context), profile),
                    ".nebula/packages");
            File[] records = recordDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
            if (records == null) continue;
            for (File recordFile : records) {
                JSONObject record;
                try {
                    record = new JSONObject(Utilities.readTextFile(recordFile, 1024 * 1024));
                } catch (Exception ignored) {
                    continue;
                }
                String packageId = record.optString("id", "").trim();
                String modName = record.optString("name", packageId).trim();
                JSONArray licenses = record.optJSONArray("licenses");
                JSONObject componentItems = record.optJSONObject("licenseComponents");
                Map<String, String> repositories = parseRepositories(record.optJSONArray("githubRepos"));
                if ((licenses == null || licenses.length() == 0) && catalogByPackage.containsKey(packageId)) {
                    licenses = new JSONArray(catalogByPackage.get(packageId).licenses);
                }
                if (licenses == null) continue;
                for (int i = 0; i < licenses.length(); i++) {
                    String id = licenses.optString(i, "").trim();
                    if (!isSpdxId(id)) continue;
                    Map<String, String> usages = componentsByLicense.computeIfAbsent(
                            id, ignored -> new LinkedHashMap<>());
                    JSONArray recordedComponents = componentItems == null ? null : componentItems.optJSONArray(id);
                    if (recordedComponents != null && recordedComponents.length() > 0) {
                        for (int componentIndex = 0; componentIndex < recordedComponents.length(); componentIndex++) {
                            String component = recordedComponents.optString(componentIndex, "").trim();
                            if (!component.isEmpty()) {
                                String repository = repositories.get(component);
                                if (repository == null && catalogByPackage.containsKey(packageId)) {
                                    repository = catalogByPackage.get(packageId).githubRepos.get(component);
                                }
                                usages.put(component, repository);
                            }
                        }
                    } else if (catalogByPackage.containsKey(packageId)
                            && catalogByPackage.get(packageId).licenseComponents.containsKey(id)) {
                        ModCatalogClient.Mod catalogMod = catalogByPackage.get(packageId);
                        for (String component : catalogMod.licenseComponents.get(id)) {
                            usages.put(component, catalogMod.githubRepos.get(component));
                        }
                    } else {
                        String repository = repositories.get(modName);
                        if (repository == null && catalogByPackage.containsKey(packageId)) {
                            repository = catalogByPackage.get(packageId).githubRepos.get(modName);
                        }
                        usages.put(modName, repository);
                    }
                }
            }
        }

        List<Usage> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : componentsByLicense.entrySet()) {
            List<Component> components = new ArrayList<>();
            for (Map.Entry<String, String> component : entry.getValue().entrySet()) {
                components.add(new Component(component.getKey(), component.getValue()));
            }
            components.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
            result.add(new Usage(entry.getKey(), Collections.unmodifiableList(components)));
        }
        result.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.spdxId, right.spdxId));
        return Collections.unmodifiableList(result);
    }

    static String getLicenseText(Context context, String spdxId) throws Exception {
        if (!isSpdxId(spdxId)) throw new IOException("Invalid SPDX identifier");
        if ("Apache-2.0 WITH LLVM-exception".equals(spdxId)) {
            try (InputStream apache = context.getAssets().open("licenses/Apache-2.0.txt");
                    InputStream exception = context.getAssets().open("licenses/LLVM-exception.txt")) {
                return readUtf8(apache, MAX_LICENSE_BYTES)
                        + "\n\n"
                        + readUtf8(exception, MAX_LICENSE_BYTES);
            }
        }
        String bundledAsset = bundledLicenseAsset(spdxId);
        if (bundledAsset != null) {
            try (InputStream input = context.getAssets().open(bundledAsset)) {
                return readUtf8(input, MAX_LICENSE_BYTES);
            }
        }
        File cacheDir = new File(context.getFilesDir(), "license-cache");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new IOException("Could not create license cache");
        File cached = new File(cacheDir, spdxId + ".txt");
        if (cached.isFile()) return Utilities.readTextFile(cached, MAX_LICENSE_BYTES);

        HttpURLConnection connection = (HttpURLConnection) new URL(
                ModCatalogClient.API_BASE + "/mods/licenses/" + spdxId).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("User-Agent", "Nebula-Android/1");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("License download failed with HTTP " + connection.getResponseCode());
            }
            String text;
            try (InputStream input = connection.getInputStream()) {
                text = readUtf8(input, MAX_LICENSE_BYTES);
            }
            if (text.trim().isEmpty()) throw new IOException("License text was empty");
            try (FileOutputStream output = new FileOutputStream(cached, false)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isSpdxId(String value) {
        return "Apache-2.0 WITH LLVM-exception".equals(value)
                || (value != null && value.matches("[A-Za-z0-9.+-]{1,128}"));
    }

    private static String bundledLicenseAsset(String spdxId) {
        switch (spdxId) {
            case "GPL-3.0-only":
                return "licenses/GPL-3.0-only.txt";
            case "MIT":
                return "licenses/MIT.txt";
            case "Apache-2.0":
                return "licenses/Apache-2.0.txt";
            case "LGPL-2.1-only":
                return "licenses/LGPL-2.1-only.txt";
            case "LGPL-3.0-only":
                return "licenses/LGPL-3.0-only.txt";
            case "BSD-2-Clause":
                return "licenses/BSD-2-Clause.txt";
            case "OFL-1.1":
                return "licenses/OFL-1.1.txt";
            case "LicenseRef-Public-Domain":
                return "licenses/LicenseRef-Public-Domain.txt";
            default:
                return null;
        }
    }

    private static void addComponent(Map<String, Map<String, String>> target,
            String license, String name, String githubUrl) {
        target.computeIfAbsent(license, ignored -> new LinkedHashMap<>()).put(name, githubUrl);
    }

    private static Map<String, String> parseRepositories(JSONArray items) {
        if (items == null) return Collections.emptyMap();
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", "").trim();
            String url = item.optString("url", "").trim();
            if (!name.isEmpty() && url.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) {
                result.put(name, url);
            }
        }
        return result;
    }

    private static String readUtf8(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maximum) throw new IOException("License text is too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Usage {
        final String spdxId;
        final List<Component> components;

        Usage(String spdxId, List<Component> components) {
            this.spdxId = spdxId;
            this.components = components;
        }
    }

    static final class Component {
        final String name;
        final String githubUrl;

        Component(String name, String githubUrl) {
            this.name = name;
            this.githubUrl = githubUrl;
        }
    }
}
