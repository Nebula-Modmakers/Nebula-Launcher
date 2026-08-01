package dev.tates.nebula;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

public class AuthorizedDevicesActivity extends Activity {
    private static final String API_BASE = "https://api.nebulaau.space";
    private View root;
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = screen();
        setContentView(root);
        Utilities.applyWindowInsets(root, dp(12));
        UiMotion.enter(root);
        load();
    }

    private View screen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.bg_nebula_screen);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.bg_nebula_button);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> UiMotion.finish(this, root));
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("Authorized Devices", 27, 0xFFF0F3FF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        page.addView(header);
        TextView summary = label("Removing a device immediately deletes every active session for that device.",
                13, 0xFF8790B9, false);
        page.addView(summary, top(dp(12)));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(12);
        page.addView(scroll, scrollParams);
        return page;
    }

    private void load() {
        list.removeAllViews();
        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        list.addView(progress, params);
        new Thread(() -> {
            try {
                String token = sessionToken();
                JSONObject response = post("/auth/device/list", new JSONObject().put("sessionToken", token));
                requireSuccess(response);
                JSONArray devices = response.optJSONArray("devices");
                runOnUiThread(() -> showDevices(devices == null ? new JSONArray() : devices));
            } catch (Exception error) {
                runOnUiThread(() -> showError(error.getMessage()));
            }
        }, "NebulaDeviceList").start();
    }

    private void showDevices(JSONArray devices) {
        list.removeAllViews();
        if (devices.length() == 0) {
            list.addView(label("No authorized devices were returned.", 14, 0xFFAAB4E8, false));
            return;
        }
        String currentId = currentDeviceId();
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null) continue;
            String id = device.optString("id", "");
            String name = device.optString("name", "Android device");
            boolean current = currentId.equals(id);
            long lastSeen = device.optLong("lastSeen", 0L);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackgroundResource(R.drawable.bg_nebula_card);
            card.addView(label(name + (current ? "  •  This device" : ""), 18, 0xFFF0F3FF, true));
            String detail = lastSeen > 0L
                    ? "Last active " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(lastSeen))
                    : "Authorized device";
            card.addView(label(detail, 13, 0xFF8790B9, false), top(dp(5)));
            Button remove = button(current ? "Remove this device" : "Remove device");
            remove.setOnClickListener(v -> confirmRemove(id, name, current));
            card.addView(remove, top(dp(12)));
            list.addView(card, top(dp(10)));
        }
    }

    private void confirmRemove(String id, String name, boolean current) {
        String message = current
                ? "This immediately signs this device out and deletes its active sessions."
                : "This immediately revokes " + name + " and deletes all of its active sessions.";
        new NebulaDialogBuilder(this)
                .setTitle("Remove authorized device?")
                .setMessage(message)
                .setPositiveButton("Remove", (dialog, which) -> remove(id, current))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void remove(String id, boolean current) {
        list.removeAllViews();
        list.addView(label("Removing device…", 14, 0xFFAAB4E8, false));
        new Thread(() -> {
            try {
                JSONObject response = post("/auth/device/remove", new JSONObject()
                        .put("sessionToken", sessionToken()).put("deviceId", id));
                requireSuccess(response);
                runOnUiThread(() -> {
                    if (current) {
                        NebulaAccountData.clearLocal(this);
                        Intent intent = new Intent(this, SelectorActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        load();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> showError(error.getMessage()));
            }
        }, "NebulaDeviceRemoval").start();
    }

    private String sessionToken() throws IOException {
        String token = SecureTokenStore.get(
                getSharedPreferences(NebulaAccountData.AUTH_PREFS, Context.MODE_PRIVATE),
                "serverSessionToken");
        if (token.isEmpty()) throw new IOException("Please log in again.");
        return token;
    }

    private String currentDeviceId() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "android:" + (id == null || id.isEmpty() ? "unknown" : id);
    }

    private JSONObject post(String path, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.2 Android");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        try {
            InputStream input = connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            return new JSONObject(read(input));
        } finally {
            connection.disconnect();
        }
    }

    private String read(InputStream input) throws IOException {
        if (input == null) return "{}";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > 64 * 1024) throw new IOException("Server response is too large");
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private void requireSuccess(JSONObject response) throws IOException {
        if (!response.optBoolean("success", false)) {
            throw new IOException(response.optString("error", "Device request failed"));
        }
    }

    private void showError(String message) {
        list.removeAllViews();
        list.addView(label(message == null || message.isEmpty() ? "Device request failed." : message,
                14, 0xFFFFA0AD, false));
        Button retry = button("Try again");
        retry.setOnClickListener(v -> load());
        list.addView(retry, top(dp(12)));
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFFF0F3FF);
        button.setBackgroundResource(R.drawable.bg_nebula_button);
        UiMotion.press(button);
        return button;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = margin;
        return params;
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { UiMotion.finish(this, root); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
