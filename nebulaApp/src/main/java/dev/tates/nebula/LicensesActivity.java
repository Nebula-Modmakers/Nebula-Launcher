package dev.tates.nebula;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class LicensesActivity extends Activity {
    private View root;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = screen();
        setContentView(root);
        Utilities.applyWindowInsets(root, dp(12));
        UiMotion.enter(root);
        new Thread(this::loadLicenses, "NebulaLicenses").start();
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
        TextView title = label("Licenses", 28, 0xFFF0F3FF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(18);
        header.addView(title, titleParams);
        page.addView(header);
        TextView subtitle = label("Bundled runtime licenses are available offline. Mod license texts download when needed and are cached.",
                13, 0xFF8790B9, false);
        page.addView(subtitle, topMargin(dp(12)));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(label("Loading installed mod licenses…", 14, 0xFFAAB4E8, false));
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(16);
        page.addView(scroll, scrollParams);
        return page;
    }

    private void loadLicenses() {
        try {
            List<LicenseRepository.Usage> usages = LicenseRepository.installedLicenseUsage(this);
            runOnUiThread(() -> showUsages(usages));
        } catch (Exception error) {
            runOnUiThread(() -> showMessage("Could not load installed mod licenses: " + error.getMessage()));
        }
    }

    private void showUsages(List<LicenseRepository.Usage> usages) {
        list.removeAllViews();
        if (usages.isEmpty()) {
            showMessage("No installed mod manifests declare licenses yet.");
            return;
        }
        for (LicenseRepository.Usage usage : usages) list.addView(licenseCard(usage), topMargin(dp(10)));
    }

    private View licenseCard(LicenseRepository.Usage usage) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.bg_nebula_card);
        card.addView(label(usage.spdxId, 18, 0xFFF0F3FF, true));
        card.addView(componentLinks(usage), topMargin(dp(6)));
        Button view = new Button(this);
        view.setText("View License Text");
        view.setAllCaps(false);
        view.setTextColor(0xFFF0F3FF);
        view.setBackgroundResource(R.drawable.bg_nebula_button);
        UiMotion.press(view);
        LinearLayout expanded = new LinearLayout(this);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setVisibility(View.GONE);
        view.setOnClickListener(v -> {
            if (expanded.getChildCount() > 0) {
                view.setVisibility(View.GONE);
                expanded.setVisibility(View.VISIBLE);
                return;
            }
            view.setEnabled(false);
            view.setText("Loading…");
            new Thread(() -> {
                try {
                    String text = LicenseRepository.getLicenseText(this, usage.spdxId);
                    runOnUiThread(() -> {
                        TextView licenseText = label(text, 12, 0xFFD5DAEC, false);
                        licenseText.setTextIsSelectable(true);
                        Button hide = new Button(this);
                        hide.setText("Hide License Text");
                        hide.setAllCaps(false);
                        hide.setTextColor(0xFFF0F3FF);
                        hide.setBackgroundResource(R.drawable.bg_nebula_button);
                        UiMotion.press(hide);
                        hide.setOnClickListener(hidden -> {
                            expanded.setVisibility(View.GONE);
                            view.setVisibility(View.VISIBLE);
                            view.setEnabled(true);
                            view.setText("View License Text");
                        });
                        expanded.addView(hide);
                        expanded.addView(licenseText, topMargin(dp(12)));
                        view.setVisibility(View.GONE);
                        expanded.setVisibility(View.VISIBLE);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        view.setEnabled(true);
                        view.setText("Retry License Download");
                        NebulaToast.makeText(this, error.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            }, "NebulaLicenseText").start();
        });
        card.addView(view, topMargin(dp(12)));
        card.addView(expanded, topMargin(dp(14)));
        return card;
    }

    private void showMessage(String message) {
        list.removeAllViews();
        list.addView(label(message, 14, 0xFFAAB4E8, false));
    }

    private TextView componentLinks(LicenseRepository.Usage usage) {
        SpannableStringBuilder text = new SpannableStringBuilder("Used by: ");
        for (int i = 0; i < usage.components.size(); i++) {
            if (i > 0) text.append(", ");
            LicenseRepository.Component component = usage.components.get(i);
            int start = text.length();
            text.append(component.name);
            if (component.githubUrl != null && !component.githubUrl.isEmpty()) {
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(component.githubUrl)));
                    }
                }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        TextView view = label("", 13, 0xFFAAB4E8, false);
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(0x334F6BFF);
        return view;
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
