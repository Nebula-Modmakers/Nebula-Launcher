package dev.tates.nebula;


import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import android.widget.Switch;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends Activity {
    private static final String TAG = "Nebula";
    private static final String NEBULA_AUTH_PREFS = "nebula_account";
    private static final String TARGET_PACKAGE = "com.innersloth.spacemafia";
    private static final String INNER_SLOTH_ACCOUNTS_URL = "https://accounts.innersloth.com/?store=google";
    private AlertDialog progressDialog;
    private View root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        root = findViewById(R.id.settings_root);
        Utilities.applyWindowInsets(root, dp(12));

        ImageButton backButton = findViewById(R.id.settings_action_back);
        backButton.setOnClickListener(v -> UiMotion.finish(this, root));
        UiMotion.press(backButton);

        Button accountLinkButton = findViewById(R.id.settings_account_link);
        accountLinkButton.setOnClickListener(v -> showAccountLinkDialog());
        UiMotion.press(accountLinkButton);

        Button replaceGameZipButton = findViewById(R.id.settings_replace_game_zip);
        replaceGameZipButton.setOnClickListener(v -> openAmongUsStore());
        UiMotion.press(replaceGameZipButton);

        Button logoutButton = findViewById(R.id.settings_logout);
        logoutButton.setOnClickListener(v -> confirmLogout());
        UiMotion.press(logoutButton);

        Button devicesButton = findViewById(R.id.settings_authorized_devices);
        devicesButton.setOnClickListener(v -> UiMotion.open(this,
                new Intent(this, AuthorizedDevicesActivity.class)));
        UiMotion.press(devicesButton);

        Button deleteAccountButton = findViewById(R.id.settings_delete_account);
        deleteAccountButton.setOnClickListener(v -> confirmDeleteAccount());
        UiMotion.press(deleteAccountButton);

        View aboutNebula = findViewById(R.id.settings_about_nebula);
        aboutNebula.setOnClickListener(v -> UiMotion.open(this,
                new Intent(this, AboutActivity.class)));
        UiMotion.press(aboutNebula);

        Switch verboseLaunch = findViewById(R.id.settings_verbose_launch);
        SharedPreferences launchPrefs = getSharedPreferences("nebula_launch", MODE_PRIVATE);
        verboseLaunch.setChecked(launchPrefs.getBoolean("verbose_launch", false));
        verboseLaunch.setOnCheckedChangeListener((button, checked) ->
                launchPrefs.edit().putBoolean("verbose_launch", checked).apply());
        UiMotion.enter(root);
        registerPredictiveBack();
    }

    private void registerPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> UiMotion.finish(this, root));
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        UiMotion.finish(this, root);
    }

    private void openAmongUsStore() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + TARGET_PACKAGE));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + TARGET_PACKAGE)));
        }
    }

    private void showProgress(String title, String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        TextView text = new TextView(this);
        int pad = dp(20);
        text.setPadding(pad, dp(8), pad, dp(2));
        text.setText(message);
        text.setTextColor(0xFF334155);
        text.setTextSize(14);
        progressDialog = new NebulaDialogBuilder(this)
                .setTitle(title)
                .setView(text)
                .setCancelable(false)
                .show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void showAccountLinkDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setHint("Paste the shared accounts.innersloth.com link");
        input.setTextColor(0xFFF0F3FF);
        input.setHintTextColor(0xFF8790B9);
        input.setBackgroundResource(R.drawable.bg_nebula_button);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        wrap.setPadding(pad, dp(8), pad, 0);
        TextView message = new TextView(this);
        message.setText("Open Innersloth, sign in with Google, use Share Link, then paste that link here.");
        message.setTextColor(0xFFAAB4E8);
        message.setTextSize(13);
        wrap.addView(message);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = dp(12);
        wrap.addView(input, inputParams);

        new NebulaDialogBuilder(this)
                .setTitle("Nebula account link")
                .setView(wrap)
                .setPositiveButton("Save", (dialog, which) -> saveAccountLink(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Open Innersloth", (dialog, which) -> openInnerslothAccountsPage())
                .show();
    }

    private void openInnerslothAccountsPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(INNER_SLOTH_ACCOUNTS_URL));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open Innersloth account page", e);
            Toast.makeText(this, "Could not open the account page.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveAccountLink(String rawLink) {
        try {
            Map<String, String> values = parseAccountLink(rawLink);
            String connectToken = firstNonEmpty(values, "EOSConnectToken", "connectToken", "token", "id_token", "googleToken");
            String userIdToken = firstNonEmpty(values, "EOSToken", "userIdToken", "token", "id_token", "googleToken");
            if (connectToken.isEmpty()) {
                Toast.makeText(this, "That link did not include an account token.", Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject tokenPayload = parseJwtPayload(connectToken);
            long connectExpiresAt = parseLong(
                    firstNonEmpty(values, "EOSConnectTokenExpiresAt", "connectTokenExpiresAt"),
                    tokenPayload.optLong("exp", 0L));
            long userIdExpiresAt = parseLong(
                    firstNonEmpty(values, "EOSTokenExpiresAt", "userIdTokenExpiresAt"),
                    connectExpiresAt);
            if (connectExpiresAt > 0L
                    && connectExpiresAt <= System.currentTimeMillis() / 1000L + 60L) {
                Toast.makeText(this, "That account link has expired. Generate a new Share Link.", Toast.LENGTH_LONG).show();
                return;
            }
            String store = firstNonEmpty(values, "store");
            String providerAccountId = firstNonEmpty(values, "accountId", "providerAccountId", "GoogleSubject", "sub");
            if (providerAccountId.isEmpty()) {
                providerAccountId = tokenPayload.optString("sub", "");
            }
            String accountId = firstNonEmpty(values, "NebulaAccountId", "LinkedAccountId", "AccountId");
            if (accountId.isEmpty() && !providerAccountId.isEmpty()) {
                accountId = (store.isEmpty() ? "google" : store.toLowerCase(Locale.US)) + ":" + providerAccountId;
            }
            int credentialType = parseInt(
                    firstNonEmpty(values, "EOSCredentialType", "credentialType"),
                    "google".equalsIgnoreCase(store) ? 12 : 15);

            StringBuilder auth = new StringBuilder();
            auth.append("Store=").append(escapeProperty(store.isEmpty() ? "google" : store)).append('\n');
            auth.append("MergeId=").append(escapeProperty(firstNonEmpty(values, "mergeId", "merge_id"))).append('\n');
            auth.append("AccountId=").append(escapeProperty(accountId)).append('\n');
            auth.append("LinkedAccountId=").append(escapeProperty(accountId)).append('\n');
            auth.append("ProviderAccountId=").append(escapeProperty(providerAccountId)).append('\n');
            auth.append("GoogleSubject=").append(escapeProperty(providerAccountId)).append('\n');
            auth.append("GoogleOAuthClientId=").append(escapeProperty(tokenPayload.optString("aud", ""))).append('\n');
            auth.append("GoogleAuthorizedParty=").append(escapeProperty(tokenPayload.optString("azp", ""))).append('\n');
            auth.append("GoogleEmail=").append(escapeProperty(tokenPayload.optString("email", ""))).append('\n');
            auth.append("GoogleAccountName=").append(escapeProperty(tokenPayload.optString("email", ""))).append('\n');
            auth.append("DisplayName=").append(escapeProperty(tokenPayload.optString("name", ""))).append('\n');
            auth.append("PictureUrl=").append(escapeProperty(tokenPayload.optString("picture", ""))).append('\n');
            auth.append("EOSToken=").append(escapeProperty(userIdToken)).append('\n');
            auth.append("EOSConnectToken=").append(escapeProperty(connectToken)).append('\n');
            auth.append("ImportedGoogleToken=").append(escapeProperty(firstNonEmpty(values, "googleToken", "id_token"))).append('\n');
            auth.append("EOSProductUserId=").append(escapeProperty(firstNonEmpty(values, "EOSProductUserId", "productUserId", "puid"))).append('\n');
            auth.append("EOSCredentialType=").append(credentialType).append('\n');
            auth.append("ImportedTokenExpiresAt=").append(connectExpiresAt).append('\n');
            auth.append("ImportedGoogleTokenExpiresAt=").append(connectExpiresAt).append('\n');
            auth.append("EOSTokenExpiresAt=").append(userIdExpiresAt).append('\n');
            auth.append("EOSConnectTokenExpiresAt=").append(connectExpiresAt).append('\n');
            auth.append("NeedsRelink=false\n");
            auth.append("LastLinkedAt=").append(System.currentTimeMillis() / 1000L).append('\n');
            auth.append("ProfilePath=").append(escapeProperty(Utilities.getPrivateTargetDirectory(this, TARGET_PACKAGE).getAbsolutePath())).append('\n');

            Utilities.writeTextFile(Utilities.getAuthConfigFile(this, TARGET_PACKAGE), auth.toString());
            Toast.makeText(this, "Nebula account link saved.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save Nebula account link", e);
            Toast.makeText(this, "Could not save that account link.", Toast.LENGTH_LONG).show();
        }
    }

    private Map<String, String> parseAccountLink(String rawLink) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (rawLink == null) {
            return result;
        }

        String text = rawLink.trim();
        int queryStart = text.indexOf('?');
        int fragmentStart = text.indexOf('#');
        if (queryStart >= 0) {
            int queryEnd = fragmentStart > queryStart ? fragmentStart : text.length();
            parseUrlParams(text.substring(queryStart + 1, queryEnd), result);
        }
        if (fragmentStart >= 0 && fragmentStart + 1 < text.length()) {
            parseUrlParams(text.substring(fragmentStart + 1), result);
        }
        if (result.isEmpty()) {
            parseUrlParams(text, result);
        }
        return result;
    }

    private void parseUrlParams(String params, Map<String, String> output) throws Exception {
        for (String part : params.split("&")) {
            int split = part.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = decodeUrl(part.substring(0, split));
            String value = decodeUrl(part.substring(split + 1));
            if (!key.isEmpty() && !value.isEmpty()) {
                output.put(key, value);
            }
        }
    }

    private String decodeUrl(String value) throws Exception {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    }

    private JSONObject parseJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return new JSONObject();
            }
            byte[] decoded = android.util.Base64.decode(parts[1],
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
            return new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String firstNonEmpty(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value == null) {
                value = values.get(key.toLowerCase(Locale.US));
            }
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String escapeProperty(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    private void confirmLogout() {
        new NebulaDialogBuilder(this)
                .setTitle("Log out of Nebula?")
                .setMessage("This signs you out of the launcher account on this device. Your mods and game files stay where they are.")
                .setPositiveButton("Log Out", (dialog, which) -> logoutNebulaAccount())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void logoutNebulaAccount() {
        SharedPreferences prefs = getSharedPreferences(NEBULA_AUTH_PREFS, MODE_PRIVATE);
        String serverSessionToken = SecureTokenStore.get(prefs, "serverSessionToken");
        if (serverSessionToken == null || serverSessionToken.isEmpty()) {
            finishLocalLogout(prefs);
            return;
        }

        showProgress("Logging out", "Deleting this device session from Nebula\u2026");
        new Thread(() -> {
            try {
                NebulaSessionApi.revoke(serverSessionToken);
                runOnUiThread(() -> finishLocalLogout(prefs));
            } catch (Exception e) {
                Log.w(TAG, "Could not revoke the Nebula server session during logout", e);
                runOnUiThread(() -> {
                    hideProgress();
                    new NebulaDialogBuilder(this)
                            .setTitle("Could not log out")
                            .setMessage("Nebula could not confirm that the server session was deleted. "
                                    + "Your account remains signed in so you can retry safely.\n\n"
                                    + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "NebulaLogout").start();
    }

    private void finishLocalLogout(SharedPreferences prefs) {
        hideProgress();
        NebulaAccountData.clearLocal(this);
        Toast.makeText(this, "Logged out of Nebula.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SelectorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void confirmDeleteAccount() {
        new NebulaDialogBuilder(this)
                .setTitle("Permanently delete your account?")
                .setMessage("This immediately deletes your Nebula profile, Firebase login, authorized devices, sessions, and all account records. Your local mods and game files remain. This cannot be undone.")
                .setPositiveButton("Continue", (dialog, which) -> confirmDeleteText())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteText() {
        EditText confirmation = new EditText(this);
        confirmation.setHint("Type DELETE");
        confirmation.setSingleLine(true);
        confirmation.setTextColor(0xFFF0F3FF);
        confirmation.setHintTextColor(0xFF8790B9);
        confirmation.setBackgroundResource(R.drawable.bg_nebula_button);
        confirmation.setPadding(dp(14), dp(12), dp(14), dp(12));
        new NebulaDialogBuilder(this)
                .setTitle("Final confirmation")
                .setView(confirmation)
                .setPositiveButton("Delete permanently", (dialog, which) -> {
                    if (!"DELETE".equals(confirmation.getText().toString().trim())) {
                        Toast.makeText(this, "Account was not deleted. Type DELETE exactly.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    deleteAccount();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteAccount() {
        SharedPreferences prefs = getSharedPreferences(NEBULA_AUTH_PREFS, MODE_PRIVATE);
        String sessionToken = SecureTokenStore.get(prefs, "serverSessionToken");
        if (sessionToken.isEmpty()) {
            Toast.makeText(this, "Please log in again before deleting the account.", Toast.LENGTH_LONG).show();
            return;
        }
        showProgress("Deleting account", "Permanently deleting Nebula account data…");
        new Thread(() -> {
            try {
                com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String idToken = user == null ? SecureTokenStore.get(prefs, "idToken")
                        : com.google.android.gms.tasks.Tasks.await(user.getIdToken(true)).getToken();
                if (idToken == null || idToken.isEmpty()) throw new IOException("Please log in again.");
                JSONObject response = deleteAccountRequest(idToken, sessionToken);
                if (!response.optBoolean("success", false)) {
                    throw new IOException(response.optString("error", "Account deletion failed"));
                }
                runOnUiThread(() -> {
                    hideProgress();
                    NebulaAccountData.clearLocal(this);
                    Toast.makeText(this, "Your Nebula account was permanently deleted.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(this, SelectorActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
            } catch (Exception error) {
                Log.w(TAG, "Account deletion failed", error);
                runOnUiThread(() -> {
                    hideProgress();
                    new NebulaDialogBuilder(this)
                            .setTitle("Account was not deleted")
                            .setMessage(error.getMessage() == null ? "Please try again." : error.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "NebulaAccountDeletion").start();
    }

    private JSONObject deleteAccountRequest(String idToken, String sessionToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://api.nebulaau.space/auth/delete-account").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + idToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.2 Android");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(new JSONObject().put("sessionToken", sessionToken).toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
        try {
            InputStream input = connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new IOException("Account deletion returned no response");
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > 64 * 1024) throw new IOException("Server response is too large");
                    output.write(buffer, 0, count);
                }
                return new JSONObject(output.toString("UTF-8"));
            }
        } finally {
            connection.disconnect();
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
