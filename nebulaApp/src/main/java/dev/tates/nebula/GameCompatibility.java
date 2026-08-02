package dev.tates.nebula;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

public final class GameCompatibility {
    public static final String AMONG_US_PACKAGE = "com.innersloth.spacemafia";
    // Among Us brands this release as 17.4a in-game, while Android exposes
    // 2026.6.5 as the package version name for the same build (7045).
    public static final String REQUIRED_VERSION = "2026.6.5";
    public static final long REQUIRED_VERSION_CODE = 7045L;

    private GameCompatibility() {}

    public static PackageInfo getPackageInfo(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.getPackageManager().getPackageInfo(
                    packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return context.getPackageManager().getPackageInfo(packageName, 0);
    }

    public static String getVersionName(PackageInfo packageInfo) {
        return packageInfo.versionName == null ? "" : packageInfo.versionName.trim();
    }

    public static long getVersionCode(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    public static boolean isSupported(PackageInfo packageInfo) {
        return REQUIRED_VERSION.equals(getVersionName(packageInfo))
                && REQUIRED_VERSION_CODE == getVersionCode(packageInfo);
    }

    public static String requiredAction(String installedVersion) {
        int comparison = compareVersion(installedVersion, REQUIRED_VERSION);
        if (comparison > 0) return "downgrade";
        if (comparison < 0) return "upgrade";
        return "install the supported build of";
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left == null ? new String[0] : left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            long leftValue = parsePart(leftParts, i);
            long rightValue = parsePart(rightParts, i);
            if (leftValue != rightValue) return Long.compare(leftValue, rightValue);
        }
        return 0;
    }

    private static long parsePart(String[] parts, int index) {
        if (index >= parts.length) return 0L;
        String value = parts[index].replaceAll("[^0-9].*$", "");
        if (value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
