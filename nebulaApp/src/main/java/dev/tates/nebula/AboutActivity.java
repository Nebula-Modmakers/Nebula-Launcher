package dev.tates.nebula;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AboutActivity extends Activity {
    private View root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = screen();
        setContentView(root);
        Utilities.applyWindowInsets(root, dp(12));
        UiMotion.enter(root);
        registerPredictiveBack();
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
        UiMotion.press(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("About Nebula", 28, 0xFFF0F3FF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        page.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundResource(R.drawable.bg_nebula_card);
        TextView name = label("Nebula", 24, 0xFFF0F3FF, true);
        TextView description = label("Native Android launcher for Nebula accounts, mods, profiles, and Among Us runtime compatibility.",
                14, 0xFFAAB4E8, false);
        TextView freeSoftware = label("Nebula is free software under GPL-3.0-only. You may share and modify it under that license. It comes without warranty.",
                13, 0xFF8790B9, false);
        Button licenses = new Button(this);
        licenses.setText("View Licenses");
        licenses.setAllCaps(false);
        licenses.setTextColor(0xFFF0F3FF);
        licenses.setBackgroundResource(R.drawable.bg_nebula_button);
        licenses.setOnClickListener(v -> UiMotion.open(this, new Intent(this, LicensesActivity.class)));
        UiMotion.press(licenses);
        Button privacy = new Button(this);
        privacy.setText("Privacy");
        privacy.setAllCaps(false);
        privacy.setTextColor(0xFFF0F3FF);
        privacy.setBackgroundResource(R.drawable.bg_nebula_button);
        privacy.setOnClickListener(v -> UiMotion.open(this, new Intent(this, PrivacyActivity.class)));
        UiMotion.press(privacy);
        card.addView(name);
        card.addView(description, topMargin(dp(8)));
        card.addView(freeSoftware, topMargin(dp(12)));
        card.addView(licenses, topMargin(dp(20)));
        card.addView(privacy, topMargin(dp(10)));
        page.addView(card, topMargin(dp(18)));
        return page;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = margin;
        return params;
    }

    private void registerPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> UiMotion.finish(this, root));
        }
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { UiMotion.finish(this, root); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
