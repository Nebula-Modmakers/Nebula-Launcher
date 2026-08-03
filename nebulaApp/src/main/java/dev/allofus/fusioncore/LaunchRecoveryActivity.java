package dev.allofus.fusioncore;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import dev.tates.nebula.BuildConfig;
import dev.tates.nebula.R;

/**
 * Restarts a wedged native game process from an isolated, visible process.
 * Android blocks PendingIntent activity launches after the foreground process dies,
 * so this activity must become visible before terminating the stalled process.
 */
public final class LaunchRecoveryActivity extends Activity {
    public static final String EXTRA_STALLED_PROCESS_ID = "stalled_process_id";
    public static final String EXTRA_RETRY_BOOTSTRAP = "retry_bootstrap";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemBars();
        setContentView(R.layout.activity_bootstrap);
        TextView title = findViewById(R.id.bootstrap_title);
        title.setText(BuildConfig.DEBUG_MODE ? "NEBULA  DEBUG" : "NEBULA");
        TextView version = findViewById(R.id.bootstrap_version);
        version.setText(BuildConfig.VERSION_NAME + "  •  NATIVE ANDROID");
        TextView status = findViewById(R.id.bootstrap_status);
        status.setText("Loading game…");

        int stalledPid = getIntent().getIntExtra(EXTRA_STALLED_PROCESS_ID, -1);
        boolean retryBootstrap = getIntent().getBooleanExtra(EXTRA_RETRY_BOOTSTRAP, true);
        String targetPackage = getIntent().getStringExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE);
        Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(() -> {
            if (stalledPid > 0 && stalledPid != android.os.Process.myPid()) {
                android.os.Process.killProcess(stalledPid);
            }
        }, 250L);

        handler.postDelayed(() -> {
            Intent destination = retryBootstrap
                    ? new Intent(this, BootstrapActivity.class)
                    : new Intent(this, dev.tates.nebula.SelectorActivity.class);
            if (retryBootstrap && targetPackage != null) {
                destination.putExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE, targetPackage);
            }
            destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(destination);
            finish();
        }, 900L);
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
