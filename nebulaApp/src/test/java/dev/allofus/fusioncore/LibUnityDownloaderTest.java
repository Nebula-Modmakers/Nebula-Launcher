package dev.allofus.fusioncore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class LibUnityDownloaderTest {
    @Test
    public void matchesSha256AcceptsKnownBytesAndRejectsModification() throws Exception {
        File file = File.createTempFile("libunity-test", ".bin");
        try {
            Files.write(file.toPath(), "nebula".getBytes(StandardCharsets.UTF_8));
            assertTrue(LibUnityDownloader.matchesSha256(
                    file,
                    "16d74232d666243e3dd9711daaef2b7538f849efaa62cf19f91a97e82c420e34"));

            Files.write(file.toPath(), "nebula!".getBytes(StandardCharsets.UTF_8));
            assertFalse(LibUnityDownloader.matchesSha256(
                    file,
                    "16d74232d666243e3dd9711daaef2b7538f849efaa62cf19f91a97e82c420e34"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void onlySupportedAmongUsRuntimeHasATrustedDigest() {
        assertTrue(LibUnityDownloader.expectedSha256("2022.3.44|arm64-v8a")
                .equals("612a259a2c3a714d6e8b28fa1885cf4002f6d89f6458a75b84a69baa3da68c06"));
        assertTrue(LibUnityDownloader.expectedSha256("2022.3.45|arm64-v8a") == null);
    }
}
