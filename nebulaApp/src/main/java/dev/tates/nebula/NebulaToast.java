package dev.tates.nebula;

import android.content.Context;

public final class NebulaToast {
    private NebulaToast() {}
    public static android.widget.Toast makeText(Context context, CharSequence text, int duration) {
        return android.widget.Toast.makeText(context, LanguageManager.translate(context, text), duration);
    }
    public static android.widget.Toast makeText(Context context, int resourceId, int duration) {
        return android.widget.Toast.makeText(context,
                LanguageManager.translate(context, context.getText(resourceId)), duration);
    }
}
