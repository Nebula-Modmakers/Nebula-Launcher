package dev.tates.nebula;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public final class ProfilesActivity extends Activity {
    private LinearLayout profilesList;
    private TextView subtitle;
    private View root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        root = findViewById(R.id.profiles_root);
        Utilities.applyWindowInsets(root, 0);
        profilesList = findViewById(R.id.profiles_list);
        subtitle = findViewById(R.id.profiles_subtitle);
        findViewById(R.id.profiles_back).setOnClickListener(view -> UiMotion.finish(this, root));
        findViewById(R.id.profiles_create).setOnClickListener(view -> showCreateProfile());
        UiMotion.press(findViewById(R.id.profiles_back));
        UiMotion.press(findViewById(R.id.profiles_create));
        UiMotion.enter(root);
        renderProfiles();
        registerPredictiveBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profilesList != null) {
            renderProfiles();
        }
    }

    private void renderProfiles() {
        profilesList.removeAllViews();
        String active = ProfileManager.getActiveName(this);
        subtitle.setText("Active profile: " + active);
        List<String> profiles = ProfileManager.list(this);
        for (String profile : profiles) {
            boolean isActive = profile.equals(active);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(16), dp(14), dp(16));
            card.setBackground(rounded(
                    isActive ? 0xEE26345D : 0xCC202A48,
                    isActive ? 0xFF63E6FF : 0x445B7CFF));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            TextView name = label(profile, 18, 0xFFF0F3FF, Typeface.BOLD);
            TextView state = label(
                    isActive ? "Active • mods are staged at launch" : "Tap to activate",
                    12,
                    isActive ? 0xFF63E6FF : 0xFF8790B9,
                    Typeface.NORMAL);
            copy.addView(name);
            copy.addView(state);
            card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (!"Default".equals(profile)) {
                Button delete = button("Delete");
                delete.setOnClickListener(view -> confirmDelete(profile));
                card.addView(delete, new LinearLayout.LayoutParams(dp(104), dp(48)));
            }

            card.setOnClickListener(view -> {
                if (profile.equals(ProfileManager.getActiveName(this))) {
                    return;
                }
                try {
                    ProfileManager.activate(this, profile);
                    renderProfiles();
                } catch (IOException exception) {
                    Toast.makeText(this, "Could not activate profile.", Toast.LENGTH_LONG).show();
                }
            });
            UiMotion.press(card);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            profilesList.addView(card, params);
        }
        UiMotion.stagger(profilesList);
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

    private void showCreateProfile() {
        EditText input = new EditText(this);
        input.setHint("Profile name");
        input.setSingleLine(true);
        input.setTextColor(0xFFF0F3FF);
        input.setHintTextColor(0xFF8790B9);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(0xEE202A48, 0x665B7CFF));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new NebulaDialogBuilder(this)
                .setTitle("New profile")
                .setMessage("Create an isolated mod collection. Activating it updates FusionCore’s plugin folder.")
                .setView(wrap)
                .setPositiveButton("Create", (dialog, which) -> {
                    String requested = input.getText().toString();
                    if (!ProfileManager.create(this, requested)) {
                        Toast.makeText(this, "Choose a unique profile name.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        ProfileManager.activate(this, requested);
                        renderProfiles();
                    } catch (IOException exception) {
                        Toast.makeText(this, "Could not activate profile.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String profile) {
        new NebulaDialogBuilder(this)
                .setTitle("Delete " + profile + "?")
                .setMessage("This removes the profile and its private mod files. The shared runtime is restaged from the active profile.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        ProfileManager.delete(this, profile);
                        renderProfiles();
                    } catch (IOException exception) {
                        Toast.makeText(this, "Could not delete profile.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private TextView label(String value, int size, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        return text;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(0xCC3A2547, 0x88F472B6));
        return button;
    }

    private GradientDrawable rounded(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
