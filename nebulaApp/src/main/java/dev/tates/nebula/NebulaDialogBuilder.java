package dev.tates.nebula;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.RequiresApi;

public final class NebulaDialogBuilder extends AlertDialog.Builder {
    public NebulaDialogBuilder(Context context) {
        super(context, R.style.NebulaAlertDialogTheme);
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        dialog.setOnShowListener(ignored -> style(dialog));
        return dialog;
    }

    @Override
    public AlertDialog show() {
        AlertDialog dialog = super.show();
        style(dialog);
        return dialog;
    }

    private static void style(AlertDialog dialog) {
        if (dialog.getWindow() != null) LanguageManager.install(dialog.getWindow().getDecorView());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.72f);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.setBlurBehindRadius(dp(dialog.getContext(), 34));
                window.setAttributes(attributes);
                window.setBackgroundBlurRadius(dp(dialog.getContext(), 18));
                blurHostBehindDialog(dialog);
            }
        }

        int parentPanelId = dialog.getContext().getResources()
                .getIdentifier("parentPanel", "id", "android");
        View parentPanel = dialog.findViewById(parentPanelId);
        if (parentPanel != null) {
            parentPanel.setBackgroundResource(R.drawable.bg_nebula_card);
            parentPanel.setClipToOutline(true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                parentPanel.setElevation(dp(dialog.getContext(), 18));
            }
        }
        clearPanelBackground(dialog, "topPanel");
        clearPanelBackground(dialog, "contentPanel");
        clearPanelBackground(dialog, "customPanel");
        clearPanelBackground(dialog, "buttonPanel");

        tintText(dialog.getWindow() == null ? null : dialog.getWindow().getDecorView());
        tintButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        tintButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        tintButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
    }

    @RequiresApi(31)
    private static void blurHostBehindDialog(AlertDialog dialog) {
        Activity activity = findActivity(dialog.getContext());
        if (activity == null) return;

        View host = activity.getWindow().getDecorView();
        float radius = dp(dialog.getContext(), 12);
        host.setRenderEffect(RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP));

        View dialogDecor = dialog.getWindow() == null
                ? null
                : dialog.getWindow().getDecorView();
        if (dialogDecor != null) {
            dialogDecor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    host.setRenderEffect(null);
                    view.removeOnAttachStateChangeListener(this);
                }
            });
        }
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    private static void tintText(View view) {
        if (view == null) return;
        if (view instanceof TextView && !(view instanceof Button)) {
            TextView text = (TextView) view;
            int id = text.getId();
            if (id == android.R.id.message || id == android.R.id.title) {
                text.setTextColor(id == android.R.id.title ? 0xFFF0F3FF : 0xFFAAB4E8);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintText(group.getChildAt(i));
            }
        }
    }

    private static void tintButton(Button button) {
        if (button != null) {
            button.setTextColor(0xFF7DD3FC);
        }
    }

    private static void clearPanelBackground(AlertDialog dialog, String name) {
        int id = dialog.getContext().getResources().getIdentifier(name, "id", "android");
        View panel = dialog.findViewById(id);
        if (panel != null) {
            panel.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
