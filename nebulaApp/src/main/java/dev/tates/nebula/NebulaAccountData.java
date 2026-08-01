package dev.tates.nebula;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;

final class NebulaAccountData {
    static final String AUTH_PREFS = "nebula_account";

    private NebulaAccountData() {}

    static void clearLocal(Context context) {
        FirebaseAuth.getInstance().signOut();
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("nebula_compat_state", Context.MODE_PRIVATE).edit().clear().commit();
        FileDeletion.delete(NebulaCompatManager.getInstalledFile(context));
    }

    private static final class FileDeletion {
        static void delete(java.io.File file) {
            if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
        }
    }
}
