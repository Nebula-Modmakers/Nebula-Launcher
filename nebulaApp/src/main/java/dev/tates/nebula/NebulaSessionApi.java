package dev.tates.nebula;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NebulaSessionApi {
    private static final String LOGOUT_URL =
            "https://api.nebulaau.space/auth/session/logout";

    private NebulaSessionApi() {
    }

    static void revoke(String sessionToken) throws Exception {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return;
        }

        HttpURLConnection connection =
                (HttpURLConnection) new URL(LOGOUT_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.0 Android");
        connection.setDoOutput(true);

        JSONObject payload = new JSONObject().put("sessionToken", sessionToken);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        StringBuilder responseText = new StringBuilder();
        if (input != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
            }
        }
        connection.disconnect();

        JSONObject response = responseText.length() == 0
                ? new JSONObject()
                : new JSONObject(responseText.toString());
        if (status < 200 || status >= 300 || !response.optBoolean("success", false)) {
            throw new IOException(response.optString(
                    "error", "The server did not confirm session deletion"));
        }
    }
}
