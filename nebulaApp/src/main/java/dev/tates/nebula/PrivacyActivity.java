package dev.tates.nebula;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyActivity extends Activity {
    private View root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = screen();
        setContentView(root);
        Utilities.applyWindowInsets(root, dp(12));
        UiMotion.enter(root);
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
        TextView title = label("Privacy", 28, 0xFFF0F3FF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(16), 0, dp(24));
        section(content, "Privacy commitment", "User data is processed only through Nebula's proprietary server and Nebula's Firebase project. Nebula never sells user data and never uses it for advertising, profiling, or purposes unrelated to providing, securing, maintaining, and supporting Nebula.");
        section(content, "Account data", "An account may contain your email, display name, username, authentication provider, and Firebase UUID. Firebase processes email/password and Google sign-in. Nebula's server does not receive or store your Google or email-account password.");
        section(content, "Authorized devices", "Nebula stores an Android-scoped device ID, readable device name, authorization time, last-active time, and sessions. These remain until the device is removed, it logs out, or the account is deleted. Removing a device immediately deletes its sessions.");
        section(content, "How information is used", "Information is used only for accounts and sign-in, user-confirmed account linking, device authorization, authenticated NebulaCompat downloads, updates, the mod catalog and licenses, service security, failure diagnosis, support, and requested deletion. Ordinary mod downloads are not authentication-gated.");
        section(content, "Logs", "Operational error and service logs are retained for no more than seven days and are used only to diagnose failures and maintain the service.");
        section(content, "Bug reports", "Nebula submits a bug report only after you review the diagnostic description and press Submit. A report may contain your description, recent Nebula and BepInEx logs, app, Android, game and mod versions, device model, and your account UUID for support and deletion. Credentials, tokens, email addresses and activation keys are automatically redacted. Bug reports are retained for up to 90 days or until your account is deleted.");
        section(content, "On this device", "Authentication tokens are encrypted with Android Keystore and excluded from backup. Logging out removes the cached NebulaCompat component but keeps local mods and game files.");
        section(content, "Retention and deletion", "Account records remain until you select Delete Account. Operational records are kept for no more than seven days, and user-submitted bug reports for no more than 90 days. Delete Account immediately removes your Firebase identity, submitted bug reports, and Nebula records tied to your UUID. Local mods and game files remain on the device.");
        section(content, "Your controls", "Settings lets you review or remove authorized devices, revoke their sessions, log out, and permanently delete your account. Android app settings can clear all Nebula data stored on the device. Account deletion cannot be undone.");
        section(content, "Security", "Nebula uses authenticated sessions, confirmed-account checks for protected downloads, Android Keystore token encryption, and private Android app storage. Protect your device and remove any device you no longer control.");
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return page;
    }

    private void section(LinearLayout content, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.bg_nebula_card);
        card.addView(label(title, 18, 0xFFF0F3FF, true));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(7);
        card.addView(label(body, 14, 0xFFAAB4E8, false), bodyParams);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        content.addView(card, cardParams);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { UiMotion.finish(this, root); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
