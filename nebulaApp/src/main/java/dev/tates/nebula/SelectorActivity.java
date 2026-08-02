package dev.tates.nebula;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import dev.allofus.fusioncore.BootstrapActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.json.JSONArray;

public class SelectorActivity extends Activity {
    private static final String TAG = "Nebula";
    private static final int REQUEST_IMPORT_MODS = 1002;
    private static final int MAX_MOD_ZIP_ENTRIES = 4096;
    private static final long MAX_MOD_ZIP_ENTRY_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_MOD_ZIP_TOTAL_BYTES = 1024L * 1024L * 1024L;
    private static final String[] SUPPORTED_PACKAGES = {
            "com.innersloth.spacemafia"
    };
    private static final String INNER_SLOTH_ACCOUNTS_URL = "https://accounts.innersloth.com/?store=google";
    private static final String NEBULA_AUTH_PREFS = "nebula_account";
    private static final String OFFLINE_GRACE_STARTED_AT = "offlineGraceStartedAt";
    private static final long OFFLINE_GRACE_MS = 2L * 24L * 60L * 60L * 1000L;
    private static final String OFFLINE_GRACE_MESSAGE = "failed to contact servers, reconnect within 2 days";
    private static final String DEACTIVATED_MESSAGE = "this account has been deactivated, contact support";
    private static final String FIREBASE_AUTH_BASE = "https://identitytoolkit.googleapis.com/v1/";
    private static final String NEBULA_API_BASE = "https://api.nebulaau.space";
    private String pendingLaunchPackage;
    private List<AppEntry> installedTargets = new ArrayList<>();
    private List<File> currentModFiles = new ArrayList<>();
    private final Map<String, String> managedModImageUrls = new HashMap<>();
    private TextView modsPathView;
    private TextView modsCountView;
    private TextView lastErrorView;
    private Button seeMoreLastErrorButton;
    private String lastErrorFullText = "";
    private TextView targetStatusView;
    private TextView launchStatusView;
    private TextView storageStatusView;
    private LinearLayout errorCardView;
    private Button clearLastErrorButton;
    private Button deleteAllModsButton;
    private ListView modsListView;
    private ArrayAdapter<File> modsAdapter;
    private TextView authStatusView;
    private int createAccountStep = 0;
    private EditText createNameInput;
    private EditText createUsernameInput;
    private EditText createEmailInput;
    private EditText createPasswordInput;
    private String pendingVerificationUid;
    private String pendingVerificationEmail;
    private String pendingVerificationUsername;
    private String pendingVerificationName;
    private String pendingVerificationIdToken;
    private String pendingVerificationRefreshToken;
    private final Handler authUiHandler = new Handler(Looper.getMainLooper());
    private Runnable authLoadingRunnable;
    private boolean verificationPolling;
    private boolean activeCheckInFlight;
    private String pendingAuthMessage;
    private Typeface orbitronTypeface;
    private boolean gameImportPromptShown;
    private boolean launchRequestInFlight;
    private boolean automaticUpdateCheckStarted;
    private AlertDialog blockingLoadingDialog;
    private FirebaseAuth firebaseAuth;
    private CredentialManager credentialManager;
    private AuthCredential pendingGoogleCredential;
    private String pendingPasswordLinkEmail;
    private String pendingPasswordLinkPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        firebaseAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);
        getSharedPreferences(NEBULA_AUTH_PREFS, Context.MODE_PRIVATE)
                .edit().remove("city").apply();
        installedTargets = resolveInstalledTargets();
        if (hasNebulaSession()) {
            validateNebulaSessionAndShowLauncher();
        } else {
            showWelcomeUi();
        }
    }

    private void showLauncherUi() {
        setContentView(createLauncherView());
        bindLauncherUi();
    }

    private void bindLauncherUi() {
        modsAdapter = new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, currentModFiles) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                LinearLayout row;
                TextView icon;
                ImageView modImage;
                TextView title;
                TextView meta;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                    FrameLayout iconHost = (FrameLayout) row.getChildAt(0);
                    icon = (TextView) iconHost.getChildAt(0);
                    modImage = (ImageView) iconHost.getChildAt(1);
                    LinearLayout textWrap = (LinearLayout) row.getChildAt(1);
                    title = (TextView) textWrap.getChildAt(0);
                    meta = (TextView) textWrap.getChildAt(1);
                } else {
                    row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    row.setBackground(roundedColor(0x99202A48, dp(18), 0x263E5EFF, dp(1)));

                    FrameLayout iconHost = new FrameLayout(getContext());
                    icon = new TextView(getContext());
                    icon.setGravity(Gravity.CENTER);
                    icon.setTextColor(0xFFFFFFFF);
                    icon.setTextSize(11);
                    icon.setTypeface(Typeface.DEFAULT_BOLD);
                    icon.setPadding(dp(9), dp(9), dp(9), dp(9));
                    icon.setBackground(roundedGradient(0xFF8B5CF6, 0xFF22D3EE, dp(15)));
                    iconHost.addView(icon, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    modImage = new ImageView(getContext());
                    modImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    modImage.setBackground(roundedGradient(0xFF8B5CF6, 0xFF22D3EE, dp(15)));
                    modImage.setClipToOutline(true);
                    modImage.setVisibility(View.GONE);
                    iconHost.addView(modImage, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    row.addView(iconHost, new LinearLayout.LayoutParams(dp(38), dp(38)));

                    LinearLayout textWrap = new LinearLayout(getContext());
                    textWrap.setOrientation(LinearLayout.VERTICAL);
                    textWrap.setPadding(dp(12), 0, 0, 0);
                    title = new TextView(getContext());
                    title.setTextColor(0xFFF0F3FF);
                    title.setTextSize(14);
                    title.setTypeface(Typeface.DEFAULT_BOLD);
                    meta = new TextView(getContext());
                    meta.setTextColor(0xFF8790B9);
                    meta.setTextSize(11);
                    textWrap.addView(title);
                    textWrap.addView(meta);
                    row.addView(textWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }

                File file = getItem(position);
                if (file != null) {
                    String kind = file.isDirectory() ? "folder" : "file";
                    boolean managed = NpkgInstaller.isManagedSharedFile(SelectorActivity.this, file);
                    boolean protectedComponent = isProtectedLauncherComponent(file);
                    title.setText(file.getName());
                    icon.setText(file.isDirectory() ? "DIR" : "M");
                    meta.setText(protectedComponent
                            ? kind + " - required by Nebula - cannot remove"
                            : managed ? kind + " - managed by Mod Store"
                            : kind + " - externally added - tap to delete");
                    String packageId = managed
                            ? NpkgInstaller.getManagedPackageId(SelectorActivity.this, file) : null;
                    String imageUrl = packageId == null ? null : managedModImageUrls.get(packageId);
                    if ("NebulaCompat.dll".equalsIgnoreCase(file.getName())) {
                        modImage.setTag("nebula-app-icon");
                        modImage.setImageResource(R.mipmap.app_icon);
                        modImage.setVisibility(View.VISIBLE);
                        icon.setVisibility(View.GONE);
                    } else if (imageUrl != null) {
                        ModIconLoader.load(imageUrl, modImage, icon);
                    } else {
                        modImage.setTag(null);
                        modImage.setImageDrawable(null);
                        modImage.setVisibility(View.GONE);
                        icon.setVisibility(View.VISIBLE);
                    }
                } else {
                    title.setText("");
                    icon.setText("M");
                    meta.setText("");
                }
                return row;
            }
        };
        modsListView.setAdapter(modsAdapter);
        modsListView.setOnItemClickListener((parent, view, position, id) -> {
            File file = currentModFiles.get(position);
            if (isProtectedLauncherComponent(file)) {
                Toast.makeText(this, "NebulaCompat is required by Nebula and cannot be removed.", Toast.LENGTH_LONG).show();
                return;
            }
            if (NpkgInstaller.isManagedSharedFile(this, file)) {
                Toast.makeText(this, "Uninstall managed mods from the Mod Store.", Toast.LENGTH_LONG).show();
                return;
            }
            confirmDeleteMod(file);
        });

        refreshModsUi();
        loadManagedModIcons();
        checkForAppUpdateAutomatically();
    }

    private void checkForAppUpdateAutomatically() {
        if (automaticUpdateCheckStarted) return;
        automaticUpdateCheckStarted = true;
        new Thread(() -> {
            try {
                AppUpdateClient.Release release = AppUpdateClient.check(this);
                if (release == null) return;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new NebulaDialogBuilder(this)
                            .setTitle("Nebula update available")
                            .setMessage("Nebula " + release.versionName
                                    + " is available. Update now to get the latest fixes and features.")
                            .setPositiveButton("View update", (dialog, which) ->
                                    UiMotion.open(this, new Intent(this, UpdatesActivity.class)))
                            .setNegativeButton("Later", null)
                            .show();
                });
            } catch (Exception error) {
                Log.w(TAG, "Automatic app update check failed", error);
            }
        }, "NebulaAppUpdateCheck").start();
    }

    private void loadManagedModIcons() {
        new Thread(() -> {
            try {
                List<ModCatalogClient.Mod> mods = ModCatalogClient.fetchCatalog();
                Map<String, String> images = new HashMap<>();
                for (ModCatalogClient.Mod mod : mods) images.put(mod.packageId, mod.imageUrl);
                runOnUiThread(() -> {
                    managedModImageUrls.clear();
                    managedModImageUrls.putAll(images);
                    if (modsAdapter != null) modsAdapter.notifyDataSetChanged();
                });
            } catch (Exception error) {
                Log.w(TAG, "Could not load managed mod icons", error);
            }
        }, "NebulaModIcons").start();
    }

    private void showLauncherUiWithSupernovaReveal() {
        FrameLayout host = findViewById(android.R.id.content);
        if (host == null || host.getChildCount() == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            showLauncherUi();
            return;
        }

        View loadingScreen = host.getChildAt(host.getChildCount() - 1);
        View launcher = createLauncherView();
        launcher.setVisibility(View.INVISIBLE);
        host.addView(launcher, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        bindLauncherUi();

        View shockwave = new View(this);
        shockwave.setBackground(roundedColor(0x0022D3EE, dp(48), 0xDD7DD3FC, dp(3)));
        shockwave.setScaleX(0.12f);
        shockwave.setScaleY(0.12f);
        host.addView(shockwave, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));

        launcher.post(() -> {
            int centerX = launcher.getWidth() / 2;
            int centerY = launcher.getHeight() / 2;
            float finalRadius = (float) Math.hypot(centerX, centerY);
            Animator reveal = ViewAnimationUtils.createCircularReveal(
                    launcher, centerX, centerY, dp(18), finalRadius);
            reveal.setDuration(980L);
            reveal.setInterpolator(new AccelerateDecelerateInterpolator());
            reveal.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    host.removeView(loadingScreen);
                    host.removeView(shockwave);
                }
            });
            launcher.setVisibility(View.VISIBLE);

            View loader = loadingScreen.findViewWithTag("nebula-auth-loader");
            if (loader != null) {
                loader.animate()
                        .scaleX(6.5f)
                        .scaleY(6.5f)
                        .alpha(0f)
                        .setDuration(840L)
                        .setInterpolator(new AccelerateInterpolator())
                        .start();
            }
            shockwave.animate()
                    .scaleX(26f)
                    .scaleY(26f)
                    .alpha(0f)
                    .setDuration(900L)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            reveal.start();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (modsListView != null) {
            refreshModsUi();
            validateActiveSessionInBackground();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMPORT_MODS && resultCode == RESULT_OK && data != null) {
            int importedCount = importSelectedMods(data);
            refreshModsUi();
            if (importedCount > 0) {
                Toast.makeText(this, getString(R.string.selector_mods_import_success, importedCount), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.selector_mods_import_failed), Toast.LENGTH_LONG).show();
            }
        }

    }

    private boolean hasNebulaSession() {
        return getSharedPreferences(NEBULA_AUTH_PREFS, Context.MODE_PRIVATE)
                .getBoolean("loggedIn", false);
    }

    private SharedPreferences nebulaAuthPrefs() {
        return getSharedPreferences(NEBULA_AUTH_PREFS, Context.MODE_PRIVATE);
    }

    private void validateNebulaSessionAndShowLauncher() {
        showLauncherUi();
        validateActiveSessionInBackground();
        maybePromptForGameZip();
        promptForStorageAccessAfterLogin();
    }

    private void validateActiveSessionInBackground() {
        if (!hasNebulaSession() || activeCheckInFlight) {
            return;
        }

        SharedPreferences prefs = nebulaAuthPrefs();
        String uid = prefs.getString("uid", "");
        String email = prefs.getString("email", "");
        String idToken = SecureTokenStore.get(prefs, "idToken");
        if (uid == null || uid.isEmpty() || idToken == null || idToken.isEmpty()) {
            logoutForDeactivatedAccount();
            return;
        }

        activeCheckInFlight = true;
        String startingIdToken = idToken;
        new Thread(() -> {
            try {
                String activeIdToken = refreshSavedIdTokenIfPossible(prefs, startingIdToken);
                ensureServerSession(prefs, activeIdToken);
                NebulaCompatManager.ensureInstalled(this,
                        SecureTokenStore.get(prefs, "serverSessionToken"));
                clearOfflineGrace();
            } catch (AccountRejectedException e) {
                Log.w(TAG, "Nebula API rejected the saved session: " + e.getMessage());
                runOnUiThread(() -> logoutForRejectedAccount(e.getMessage()));
            } catch (Exception e) {
                Log.w(TAG, "Could not verify Nebula API session", e);
                if (isNetworkContactFailure(e)) {
                    runOnUiThread(this::handleActiveCheckFailure);
                }
            } finally {
                activeCheckInFlight = false;
            }
        }, "NebulaActiveCheck").start();
    }

    private void logoutForDeactivatedAccount() {
        logoutForRejectedAccount(DEACTIVATED_MESSAGE);
    }

    private void logoutForRejectedAccount(String reason) {
        nebulaAuthPrefs().edit().clear().apply();
        resetAuthCreateFields();
        pendingAuthMessage = reason == null || reason.isEmpty() ? "Please log back in." : reason;
        Toast.makeText(this, pendingAuthMessage, Toast.LENGTH_LONG).show();
        showWelcomeUi();
    }

    private void handleActiveCheckFailure() {
        SharedPreferences prefs = nebulaAuthPrefs();
        long now = System.currentTimeMillis();
        long startedAt = prefs.getLong(OFFLINE_GRACE_STARTED_AT, 0L);
        if (startedAt <= 0L) {
            prefs.edit().putLong(OFFLINE_GRACE_STARTED_AT, now).apply();
            showOfflineGraceWarning();
            return;
        }
        if (now - startedAt >= OFFLINE_GRACE_MS) {
            prefs.edit().clear().apply();
            resetAuthCreateFields();
            pendingAuthMessage = "Please log back in.";
            Toast.makeText(this, "Please log back in.", Toast.LENGTH_LONG).show();
            showWelcomeUi();
            return;
        }
        showOfflineGraceWarning();
    }

    private void clearOfflineGrace() {
        nebulaAuthPrefs().edit().remove(OFFLINE_GRACE_STARTED_AT).apply();
    }

    private void showOfflineGraceWarning() {
        new NebulaDialogBuilder(this)
                .setMessage(OFFLINE_GRACE_MESSAGE)
                .setPositiveButton("Dismiss", null)
                .show();
    }

    private String refreshSavedIdTokenIfPossible(SharedPreferences prefs, String currentIdToken) throws Exception {
        FirebaseUser sdkUser = firebaseAuth == null ? null : firebaseAuth.getCurrentUser();
        String expectedUid = prefs.getString("uid", "");
        if (sdkUser != null && sdkUser.getUid().equals(expectedUid)) {
            String freshToken = com.google.android.gms.tasks.Tasks.await(
                    sdkUser.getIdToken(false)).getToken();
            if (freshToken != null && !freshToken.isEmpty()) {
                SecureTokenStore.put(prefs, "idToken", freshToken);
                return freshToken;
            }
        }
        String refreshToken = SecureTokenStore.get(prefs, "refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return currentIdToken;
        }

        JSONObject refreshed = firebaseRefreshIdToken(refreshToken);
        String idToken = refreshed.optString("id_token", "");
        String newRefreshToken = refreshed.optString("refresh_token", refreshToken);
        if (!idToken.isEmpty()) {
            SecureTokenStore.put(prefs, "idToken", idToken);
            SecureTokenStore.put(prefs, "refreshToken", newRefreshToken);
            return idToken;
        }
        return currentIdToken;
    }

    private void beginGoogleSignIn() {
        showAuthLoadingUi("Google sign in", "Choose the Google account for Nebula", 0xFF22D3EE);
        startAuthLoading("Waiting for Google");
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        credentialManager.getCredentialAsync(
                this,
                request,
                new android.os.CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException error) {
                        Log.w(TAG, "Google credential request failed", error);
                        runOnUiThread(() -> {
                            stopAuthLoading();
                            showLoginUi();
                            setAuthStatus("Google sign in was cancelled or unavailable.");
                        });
                    }
                });
    }

    private void handleGoogleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            runOnUiThread(() -> {
                showLoginUi();
                setAuthStatus("Google returned an unsupported credential.");
            });
            return;
        }
        try {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(
                    ((CustomCredential) credential).getData());
            String googleIdToken = google.getIdToken();
            pendingGoogleCredential = GoogleAuthProvider.getCredential(googleIdToken, null);
            new Thread(() -> preflightGoogleSignIn(google.getId(), googleIdToken),
                    "NebulaGooglePreflight").start();
        } catch (Exception error) {
            Log.w(TAG, "Could not parse Google credential", error);
            runOnUiThread(() -> {
                showLoginUi();
                setAuthStatus("Could not read the Google credential.");
            });
        }
    }

    private void preflightGoogleSignIn(String email, String googleIdToken) {
        try {
            JSONObject response = apiPost("/auth/google-provider-state",
                    new JSONObject().put("googleIdToken", googleIdToken), null);
            requireApiSuccess(response);
            String state = response.optString("state", "other");
            runOnUiThread(() -> {
                if ("password-only".equals(state)) {
                    stopAuthLoading();
                    showLoginUi(email);
                    promptPasswordToLinkGoogle(email);
                    return;
                }
                signInWithPendingGoogleCredential(email);
            });
        } catch (Exception error) {
            Log.w(TAG, "Google provider preflight failed", error);
            runOnUiThread(() -> {
                pendingGoogleCredential = null;
                stopAuthLoading();
                showLoginUi(email);
                setAuthStatus("Could not safely check whether this Google account needs linking.");
            });
        }
    }

    private void signInWithPendingGoogleCredential(String email) {
        if (pendingGoogleCredential == null) {
            showLoginUi(email);
            setAuthStatus("Google credential expired. Please try again.");
            return;
        }
        firebaseAuth.signInWithCredential(pendingGoogleCredential)
                    .addOnSuccessListener(this, result -> completeGoogleFirebaseSignIn(result.getUser()))
                    .addOnFailureListener(this, error -> {
                        if (error instanceof FirebaseAuthUserCollisionException) {
                            verifyPasswordLinkCollision(email);
                        } else {
                            Log.w(TAG, "Firebase Google sign in failed", error);
                            showLoginUi(email);
                            setAuthStatus(readableAuthError(new Exception(error.getMessage(), error)));
                        }
                    });
    }

    private void verifyPasswordLinkCollision(String email) {
        // Firebase only reports this collision when the verified Google email is
        // already bound to another sign-in provider. The server-side provider
        // state is checked after that provider's password has been proven.
        promptPasswordToLinkGoogle(email);
    }

    private void promptPasswordToLinkGoogle(String email) {
        EditText password = authInput("Password for " + email, true);
        new NebulaDialogBuilder(this)
                .setTitle("Link Google to your Nebula account")
                .setMessage("A password account already uses this email. Enter its password once to prove ownership and link Google securely.")
                .setView(password)
                .setPositiveButton("Link", (dialog, which) -> {
                    showAuthLoadingUi("Linking accounts", "Verifying both sign-in methods", 0xFF22D3EE);
                    firebaseAuth.signInWithEmailAndPassword(email, password.getText().toString())
                            .addOnSuccessListener(this, result -> {
                                FirebaseUser user = result.getUser();
                                if (user == null || pendingGoogleCredential == null) {
                                    showLoginUi(email);
                                    setAuthStatus("Could not link the accounts.");
                                    return;
                                }
                                user.getIdToken(true).addOnSuccessListener(this, token ->
                                        new Thread(() -> verifyAndLinkGoogleCredential(
                                                user, email, token.getToken()),
                                                "NebulaProviderCheck").start())
                                        .addOnFailureListener(this, error -> {
                                            showLoginUi(email);
                                            setAuthStatus("Could not verify the existing account.");
                                        });
                            })
                            .addOnFailureListener(this, error -> {
                                showLoginUi(email);
                                setAuthStatus("That password was incorrect, so no accounts were linked.");
                            });
                })
                .setNegativeButton("Cancel", (dialog, which) -> showLoginUi(email))
                .show();
    }

    private void verifyAndLinkGoogleCredential(FirebaseUser user, String email, String idToken) {
        try {
            if (!"password-only".equals(providerState(idToken))) {
                throw new Exception("The server did not confirm a password-only account.");
            }
            runOnUiThread(() -> user.linkWithCredential(pendingGoogleCredential)
                    .addOnSuccessListener(this, linked -> completeGoogleFirebaseSignIn(linked.getUser()))
                    .addOnFailureListener(this, error -> {
                        Log.w(TAG, "Google account linking failed", error);
                        showLoginUi(email);
                        setAuthStatus("Could not link Google: " + error.getMessage());
                    }));
        } catch (Exception error) {
            Log.w(TAG, "Server rejected Google linking state", error);
            runOnUiThread(() -> {
                firebaseAuth.signOut();
                showLoginUi(email);
                setAuthStatus("The server could not confirm that this account is password-only.");
            });
        }
    }

    private void completeGoogleFirebaseSignIn(FirebaseUser user) {
        if (user == null) {
            showLoginUi();
            setAuthStatus("Google sign in did not return an account.");
            return;
        }
        if (pendingPasswordLinkEmail != null) {
            String googleEmail = user.getEmail() == null ? ""
                    : user.getEmail().trim().toLowerCase(Locale.US);
            if (!pendingPasswordLinkEmail.equals(googleEmail)) {
                pendingPasswordLinkEmail = null;
                pendingPasswordLinkPassword = null;
                firebaseAuth.signOut();
                showLoginUi();
                setAuthStatus("Choose the Google account with the same email.");
                return;
            }
            String candidatePassword = pendingPasswordLinkPassword;
            user.getIdToken(true).addOnSuccessListener(this, token ->
                    new Thread(() -> verifyGooglePasswordOption(
                            user, candidatePassword, token.getToken()),
                            "NebulaProviderCheck").start())
                    .addOnFailureListener(this, error -> {
                        pendingPasswordLinkEmail = null;
                        pendingPasswordLinkPassword = null;
                        continueGoogleLogin(user);
                    });
            return;
        }
        continueGoogleLogin(user);
    }

    private void verifyGooglePasswordOption(FirebaseUser user, String candidatePassword, String idToken) {
        String state;
        try {
            state = providerState(idToken);
        } catch (Exception error) {
            Log.w(TAG, "Could not confirm Google provider state", error);
            state = "unknown";
        }
        String confirmedState = state;
        runOnUiThread(() -> {
            pendingPasswordLinkEmail = null;
            pendingPasswordLinkPassword = null;
            if (!"google-only".equals(confirmedState)) {
                continueGoogleLogin(user);
                return;
            }
            new NebulaDialogBuilder(this)
                    .setTitle("Google account verified")
                    .setMessage("Continue with Google, or add the password you just entered so either method works next time.")
                    .setPositiveButton("Add password", (dialog, which) -> user.updatePassword(candidatePassword)
                            .addOnSuccessListener(this, ignored -> continueGoogleLogin(user))
                            .addOnFailureListener(this, error -> {
                                Log.w(TAG, "Could not add password provider", error);
                                continueGoogleLogin(user);
                                Toast.makeText(this, "Signed in with Google; password was not added.", Toast.LENGTH_LONG).show();
                            }))
                    .setNegativeButton("Google only", (dialog, which) -> continueGoogleLogin(user))
                    .show();
        });
    }

    private void continueGoogleLogin(FirebaseUser user) {
        showAuthLoadingUi("Google sign in", "Opening your Nebula account", 0xFF22D3EE);
        startAuthLoading("Checking your profile");
        user.getIdToken(true).addOnSuccessListener(this, tokenResult -> {
            String idToken = tokenResult.getToken();
            new Thread(() -> finishGoogleLogin(user, idToken), "NebulaGoogleLogin").start();
        }).addOnFailureListener(this, error -> {
            showLoginUi(user.getEmail());
            setAuthStatus("Could not create a Firebase session.");
        });
    }

    private void finishGoogleLogin(FirebaseUser user, String idToken) {
        try {
            String email = user.getEmail() == null ? "" : user.getEmail();
            JSONObject profileSync = apiPost("/auth/sync-google-profile", new JSONObject(), idToken);
            requireApiSuccess(profileSync);
            JSONObject fields = getUserFieldsForLogin(user.getUid(), email, idToken);
            if (!hasProfileName(fields)) {
                runOnUiThread(() -> promptGoogleProfileSetup(user, idToken));
                return;
            }
            if (!isAccountActive(fields)) throw new Exception(DEACTIVATED_MESSAGE);
            String city = fetchUserCity();
            if (!firestoreBool(fields, "activated")) {
                JSONObject activation = apiPost("/auth/activate", new JSONObject().put("city", city), idToken);
                requireApiSuccess(activation);
            }
            JSONObject session = createServerSession(idToken);
            String username = firestoreString(fields, "username");
            String name = firestoreString(fields, "name");
            runOnUiThread(() -> finishGoogleUi(user, email, username, name, city, idToken, session));
        } catch (Exception error) {
            Log.w(TAG, "Google Nebula login failed", error);
            runOnUiThread(() -> {
                showLoginUi(user.getEmail());
                setAuthStatus(readableAuthError(error));
            });
        }
    }

    private void promptGoogleProfileSetup(FirebaseUser user, String idToken) {
        stopAuthLoading();
        LinearLayout content = authShell();
        TextView title = text("Finish Your Account", 26, 0xFFFFFFFF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        TextView subtitle = text("Google is verified. Complete your Nebula profile to continue.",
                14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = authInput("Name", false);
        name.setText(user.getDisplayName() == null ? "" : user.getDisplayName());
        name.setEnabled(false);
        name.setTextColor(0xFF9AA3B8);
        name.setHintTextColor(0xFF6B7280);
        name.setBackground(roundedColor(0xFF202532, dp(18), 0x334B5563, dp(1)));
        EditText username = authInput("Username", false);
        form.addView(name);
        form.addView(withTopMargin(username, dp(8)));
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button back = secondaryAuthButton("Back");
        Button create = primaryAuthButton("Create Account");
        authStatusView = authStatus();
        back.setOnClickListener(v -> showLoginUi(user.getEmail()));
        create.setOnClickListener(v -> {
            String googleName = name.getText().toString().trim();
            String chosenUsername = username.getText().toString().trim();
            if (googleName.isEmpty() || chosenUsername.isEmpty()) {
                setAuthStatus("Enter a username.");
                return;
            }
            submitGoogleProfileSetup(user, idToken, googleName, chosenUsername);
        });
        nav.addView(back, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        createParams.leftMargin = dp(10);
        nav.addView(create, createParams);
        content.addView(title);
        content.addView(withTopMargin(subtitle, dp(8)));
        content.addView(withTopMargin(createStepProgress(5), dp(18)));
        content.addView(withTopMargin(form, dp(24)));
        content.addView(withTopMargin(privacyAgreementView(), dp(14)));
        content.addView(withTopMargin(nav, dp(22)));
        content.addView(withTopMargin(authStatusView, dp(14)));
        setContentView(authRoot(content, 1));
    }

    private void submitGoogleProfileSetup(FirebaseUser user, String idToken, String name,
            String username) {
        showAuthLoadingUi("Creating account", "Setting up your Nebula profile", 0xFF22D3EE);
        new Thread(() -> {
            try {
                String city = fetchUserCity();
                JSONObject registration = apiPost("/auth/register", new JSONObject()
                        .put("name", name)
                        .put("username", username)
                        .put("city", city), idToken);
                requireApiSuccess(registration);
                JSONObject activation = apiPost("/auth/activate", new JSONObject().put("city", city), idToken);
                requireApiSuccess(activation);
                JSONObject session = createServerSession(idToken);
                runOnUiThread(() -> finishGoogleUi(user, user.getEmail(), username, name,
                        city, idToken, session));
            } catch (Exception error) {
                Log.w(TAG, "Google profile setup failed", error);
                runOnUiThread(() -> {
                    promptGoogleProfileSetup(user, idToken);
                    setAuthStatus(readableAuthError(error));
                });
            }
        }, "NebulaGoogleRegister").start();
    }

    private void finishGoogleUi(FirebaseUser user, String email, String username, String name,
            String city, String idToken, JSONObject session) {
        stopAuthLoading();
        saveNebulaSession(user.getUid(), email, username, name, city, idToken, "", true,
                session.optString("sessionToken", ""), session.optLong("expiresAt", 0L));
        Toast.makeText(this, "Signed in with Google.", Toast.LENGTH_SHORT).show();
        showLauncherUiWithSupernovaReveal();
        promptForStorageAccessAfterLogin();
    }

    private boolean isNetworkContactFailure(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof java.net.UnknownHostException) {
                return true;
            }
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void showWelcomeUi() {
        verificationPolling = false;
        stopAuthLoading();
        LinearLayout content = authShell();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nebula_logo_rounded);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setGravity(Gravity.CENTER);
        TextView welcome = text("Welcome to", 20, 0xFFAAB4E8, Typeface.BOLD);
        welcome.setGravity(Gravity.CENTER);
        title.addView(welcome);
        title.addView(nebulaWordmark(32, 0xFFFFFFFF, 0xFF22D3EE, Gravity.CENTER));
        TextView subtitle = text("Your modded Among Us launcher, account link, and mod library in one place.", 14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        Button start = primaryAuthButton("Get Started");
        start.setOnClickListener(v -> showLoginUi());
        authStatusView = authStatus();
        String initialMessage = pendingAuthMessage;
        pendingAuthMessage = null;
        if (initialMessage != null && !initialMessage.isEmpty()) {
            authStatusView.setText(initialMessage);
            authStatusView.setTextColor(0xFFFFB4AB);
        }

        content.addView(logo, centeredParams(dp(84), dp(84), dp(18)));
        content.addView(title);
        content.addView(withTopMargin(subtitle, dp(10)));
        content.addView(withTopMargin(start, dp(28)));
        if (initialMessage != null && !initialMessage.isEmpty()) {
            content.addView(withTopMargin(authStatusView, dp(14)));
        }
        setContentView(authRoot(content));
    }

    private void showLoginUi() {
        showLoginUi("");
    }

    private void showLoginUi(String prefillEmail) {
        verificationPolling = false;
        stopAuthLoading();
        LinearLayout content = authShell();
        View title = nebulaWordmark(32, 0xFFFFFFFF, 0xFF8B5CF6, Gravity.CENTER);
        TextView subtitle = text("Log in to continue", 14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        EditText email = authInput("Email", false);
        if (prefillEmail != null && !prefillEmail.trim().isEmpty()) {
            email.setText(prefillEmail.trim());
            email.setSelection(email.getText().length());
        }
        EditText password = authInput("Password", true);
        Button login = primaryAuthButton("Log In");
        ImageButton google = googleAuthButton();
        Button create = secondaryAuthButton("Create Account");
        Button forgot = secondaryAuthButton("Forgot Password");
        authStatusView = authStatus();

        login.setOnClickListener(v -> loginNebulaAccount(email.getText().toString(), password.getText().toString()));
        google.setOnClickListener(v -> beginGoogleSignIn());
        create.setOnClickListener(v -> showCreateAccountUi(0));
        forgot.setOnClickListener(v -> sendNebulaPasswordReset(email.getText().toString()));

        content.addView(title);
        content.addView(withTopMargin(subtitle, dp(8)));
        content.addView(withTopMargin(email, dp(24)));
        content.addView(withTopMargin(password, dp(12)));
        content.addView(withTopMargin(login, dp(18)));
        content.addView(withTopMargin(create, dp(12)));
        content.addView(withTopMargin(forgot, dp(12)));
        content.addView(withTopMargin(googleAuthSection(google), dp(20)));
        content.addView(withTopMargin(authStatusView, dp(14)));
        setContentView(authRoot(content));
    }

    private void showVerifyEmailUi() {
        LinearLayout content = authShell();
        TextView icon = authIcon("@");
        TextView title = text("Verify your email", 28, 0xFFFFFFFF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        TextView subtitle = text("We sent a verification email to " + pendingVerificationEmail + ". Verify it, then continue here.", 14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        Button resend = secondaryAuthButton("Resend Email");
        Button back = secondaryAuthButton("Back to Log In");
        authStatusView = authStatus();

        resend.setOnClickListener(v -> resendPendingVerificationEmail());
        back.setOnClickListener(v -> {
            verificationPolling = false;
            stopAuthLoading();
            showLoginUi(pendingVerificationEmail);
        });

        content.addView(icon, centeredParams(dp(74), dp(74), dp(18)));
        content.addView(title);
        content.addView(withTopMargin(subtitle, dp(10)));
        content.addView(withTopMargin(resend, dp(28)));
        content.addView(withTopMargin(back, dp(12)));
        content.addView(withTopMargin(authStatusView, dp(14)));
        setContentView(authRoot(content));
        authUiHandler.postDelayed(this::startPendingEmailVerificationCheck, 450);
    }

    private void showCreateAccountUi(int step) {
        showCreateAccountUi(step, 1);
    }

    private void showCreateAccountUi(int step, int slideDirection) {
        stopAuthLoading();
        createAccountStep = Math.max(0, Math.min(step, 4));
        LinearLayout content = authShell();
        TextView title = text("Create Account", 26, 0xFFFFFFFF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        TextView subtitle = text(createStepSubtitle(createAccountStep), 14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout progress = createStepProgress(createAccountStep);
        View stepView = createStepView(createAccountStep);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        Button back = secondaryAuthButton(createAccountStep == 0 ? "Back" : "Previous");
        Button next = primaryAuthButton(createAccountStep == 4 ? "Create Account" : "Next");
        authStatusView = authStatus();

        back.setOnClickListener(v -> {
            if (createAccountStep == 0) {
                showLoginUi();
            } else {
                showCreateAccountUi(createAccountStep - 1, -1);
            }
        });
        next.setOnClickListener(v -> {
            if (createAccountStep < 4) {
                validateCreateStepThenAdvance(createAccountStep);
            } else {
                registerNebulaAccount();
            }
        });

        nav.addView(back, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        nextParams.leftMargin = dp(10);
        nav.addView(next, nextParams);

        content.addView(title);
        content.addView(withTopMargin(subtitle, dp(8)));
        content.addView(withTopMargin(progress, dp(18)));
        content.addView(withTopMargin(stepView, dp(24)));
        content.addView(withTopMargin(nav, dp(22)));
        content.addView(withTopMargin(authStatusView, dp(14)));
        setContentView(authRoot(content, slideDirection));
    }

    private String createStepSubtitle(int step) {
        switch (step) {
            case 0:
                return "First, what should we call you?";
            case 1:
                return "Choose your username.";
            case 2:
                return "What's your email?";
            case 3:
                return "Secure the account.";
            default:
                return "Review your account before creating it.";
        }
    }

    private View createStepView(int step) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        switch (step) {
            case 0:
                if (createNameInput == null) {
                    createNameInput = authInput("Full name", false);
                }
                addReusableView(wrap, createNameInput);
                break;
            case 1:
                if (createUsernameInput == null) {
                    createUsernameInput = authInput("Username", false);
                }
                addReusableView(wrap, createUsernameInput);
                break;
            case 2:
                if (createEmailInput == null) {
                    createEmailInput = authInput("Email", false);
                }
                addReusableView(wrap, createEmailInput);
                break;
            case 3:
                if (createPasswordInput == null) {
                    createPasswordInput = authInput("Password", true);
                }
                addReusableView(wrap, createPasswordInput);
                break;
            default:
                wrap.addView(reviewRow("Name", valueOrPlaceholder(createNameInput, "Not provided")));
                wrap.addView(reviewRow("Username", valueOrPlaceholder(createUsernameInput, "Not provided")));
                wrap.addView(reviewRow("Email", valueOrPlaceholder(createEmailInput, "Not provided")));
                wrap.addView(privacyAgreementView());
                break;
        }
        return wrap;
    }

    private LinearLayout createStepProgress(int activeStep) {
        LinearLayout progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            View dot = new View(this);
            dot.setBackground(roundedColor(i <= activeStep ? 0xFF22D3EE : 0x553A4157, dp(4), Color.TRANSPARENT, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(5), 1f);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            progress.addView(dot, params);
        }
        return progress;
    }

    private TextView reviewRow(String label, String value) {
        TextView row = text(label + "\n" + value, 14, 0xFFF0F3FF, Typeface.BOLD);
        row.setBackground(roundedColor(0x99202A48, dp(16), 0x263E5EFF, dp(1)));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);
        return row;
    }

    private View privacyAgreementView() {
        LinearLayout agreement = new LinearLayout(this);
        agreement.setOrientation(LinearLayout.VERTICAL);
        agreement.setPadding(dp(14), dp(12), dp(14), dp(12));
        agreement.setBackground(roundedColor(0x99202A48, dp(16), 0x263E5EFF, dp(1)));

        TextView notice = text("By creating an account on Nebula, you confirm that you have read and agree to the Privacy Policy.",
                13, 0xFFAAB4E8, Typeface.NORMAL);
        agreement.addView(notice);

        Button readPolicy = secondaryAuthButton("Read Privacy Policy");
        readPolicy.setOnClickListener(v -> startActivity(new Intent(this, PrivacyActivity.class)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        buttonParams.topMargin = dp(10);
        agreement.addView(readPolicy, buttonParams);
        return agreement;
    }

    private View verificationStepView() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView message = text("We sent a verification email to " + valueOrPlaceholder(createEmailInput, "your email") + ". Once it is verified, Nebula will continue automatically.", 14, 0xFFF0F3FF, Typeface.BOLD);
        message.setGravity(Gravity.CENTER);
        message.setBackground(roundedColor(0x99202A48, dp(16), 0x263E5EFF, dp(1)));
        message.setPadding(dp(14), dp(14), dp(14), dp(14));
        wrap.addView(message);
        return wrap;
    }

    private String valueOrPlaceholder(EditText input, String placeholder) {
        if (input == null) {
            return placeholder;
        }
        String value = input.getText().toString().trim();
        return value.isEmpty() ? placeholder : value;
    }

    private void addReusableView(ViewGroup parent, View child) {
        if (child.getParent() instanceof ViewGroup) {
            ViewGroup oldParent = (ViewGroup) child.getParent();
            oldParent.removeView(child);
        }
        parent.addView(child);
    }

    private View authRoot(View content) {
        return authRoot(content, 0);
    }

    private View authRoot(View content, int slideDirection) {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(roundedGradient(0xFF0C0F1E, 0xFF171D36, 0));

        View glowTop = new View(this);
        glowTop.setAlpha(0.38f);
        glowTop.setBackground(roundedGradient(0x883E5EFF, 0x0022D3EE, dp(170)));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(dp(340), dp(340), Gravity.TOP | Gravity.RIGHT);
        topParams.topMargin = -dp(150);
        topParams.rightMargin = -dp(150);
        root.addView(glowTop, topParams);

        View glowBottom = new View(this);
        glowBottom.setAlpha(0.34f);
        glowBottom.setBackground(roundedGradient(0x806FCF97, 0x000C0F1E, dp(160)));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(dp(320), dp(320), Gravity.BOTTOM | Gravity.LEFT);
        bottomParams.leftMargin = -dp(150);
        bottomParams.bottomMargin = -dp(150);
        root.addView(glowBottom, bottomParams);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        contentParams.leftMargin = dp(10);
        contentParams.rightMargin = dp(10);
        contentParams.topMargin = dp(18);
        contentParams.bottomMargin = dp(18);
        root.addView(content, contentParams);
        if (slideDirection == 0) {
            animateStaggeredChildren((ViewGroup) content, 58);
        } else {
            content.setAlpha(0f);
            content.setTranslationX(slideDirection * dp(120));
            content.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(360)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        root.setAlpha(0f);
        root.setTranslationY(dp(16));
        root.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        return root;
    }

    private LinearLayout authShell() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(32), dp(28), dp(32));
        card.setBackground(roundedColor(0xE51B1F2C, dp(30), 0x334F7CFF, dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(9));
        }
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private TextView authIcon(String value) {
        TextView icon = text(value, 28, Color.WHITE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundedGradient(0xFF6366F1, 0xFF22D3EE, dp(24)));
        return icon;
    }

    private EditText authInput(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF7F8AB7);
        input.setTextSize(15);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        input.setMinHeight(dp(68));
        input.setBackground(roundedColor(0xFF2A3040, dp(18), 0x334F7CFF, dp(1)));
        if (password) {
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (hint.toLowerCase(Locale.US).contains("email")) {
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        addFocusLift(input);
        return input;
    }

    private Button primaryAuthButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedGradient(0xFF4D7CFF, 0xFF22D3EE, dp(18)));
        addTapScale(button);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return button;
    }

    private Button secondaryAuthButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(0xFFF0F3FF);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedColor(0xFF3A4157, dp(18), 0x334F7CFF, dp(1)));
        addTapScale(button);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return button;
    }

    private ImageButton googleAuthButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_google_g);
        button.setContentDescription("Continue with Google");
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(15), dp(15), dp(15), dp(15));
        button.setBackground(roundedColor(0xFFFFFFFF, dp(30), 0x334F7CFF, dp(1)));
        button.setElevation(dp(3));
        addTapScale(button);
        return button;
    }

    private View googleAuthSection(ImageButton googleButton) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.HORIZONTAL);
        divider.setGravity(Gravity.CENTER_VERTICAL);
        View leftLine = new View(this);
        leftLine.setBackgroundColor(0x554F7CFF);
        View rightLine = new View(this);
        rightLine.setBackgroundColor(0x554F7CFF);
        TextView or = text("OR", 12, 0xFF8F9AC7, Typeface.BOLD);
        or.setGravity(Gravity.CENTER);
        divider.addView(leftLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        LinearLayout.LayoutParams orParams = new LinearLayout.LayoutParams(dp(62), dp(32));
        divider.addView(or, orParams);
        divider.addView(rightLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        section.addView(divider);

        FrameLayout googlePanel = new FrameLayout(this);
        googlePanel.setBackground(roundedColor(0x221B2135, dp(22), 0x885B7CFF, dp(2)));
        googlePanel.setContentDescription("Alternative sign in options");
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(60), dp(60));
        buttonParams.gravity = Gravity.CENTER;
        googlePanel.addView(googleButton, buttonParams);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        panelParams.topMargin = dp(8);
        section.addView(googlePanel, panelParams);
        return section;
    }

    private TextView authStatus() {
        TextView status = text("", 13, 0xFF8ECBFF, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        return status;
    }

    private View withTopMargin(View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout.LayoutParams centeredParams(int width, int height, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = bottomMargin;
        return params;
    }

    private void registerNebulaAccount() {
        String name = valueOrPlaceholder(createNameInput, "").trim();
        String username = valueOrPlaceholder(createUsernameInput, "").trim();
        String email = valueOrPlaceholder(createEmailInput, "").trim();
        String password = valueOrPlaceholder(createPasswordInput, "");
        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            setAuthStatus("Finish every step first.");
            return;
        }

        showAuthLoadingUi("Creating account", "Setting up your Nebula profile", 0xFF22D3EE);
        startAuthLoading("Creating your account");
        runAuthRequest(() -> {
            JSONObject signup = new JSONObject();
            signup.put("email", email);
            signup.put("password", password);
            signup.put("returnSecureToken", true);
            JSONObject authResult = firebaseAuthPost("accounts:signUp", signup);
            String uid = authResult.getString("localId");
            String idToken = authResult.getString("idToken");
            String refreshToken = authResult.optString("refreshToken", "");

            JSONObject verify = new JSONObject();
            verify.put("requestType", "VERIFY_EMAIL");
            verify.put("idToken", idToken);
            firebaseAuthPost("accounts:sendOobCode", verify);

            String city = fetchUserCity();
            JSONObject registration = apiPost("/auth/register", new JSONObject()
                    .put("name", name)
                    .put("username", username)
                    .put("city", city), idToken);
            requireApiSuccess(registration);

            runOnUiThread(() -> {
                pendingVerificationUid = uid;
                pendingVerificationEmail = email;
                pendingVerificationUsername = username;
                pendingVerificationName = name;
                pendingVerificationIdToken = idToken;
                pendingVerificationRefreshToken = refreshToken;
                Toast.makeText(this, "Verification email sent.", Toast.LENGTH_LONG).show();
                showVerifyEmailUi();
            });
        });
    }

    private void validateCreateStepThenAdvance(int step) {
        switch (step) {
            case 0:
                if (valueOrPlaceholder(createNameInput, "").trim().isEmpty()) {
                    setAuthStatus("Enter your name.");
                    return;
                }
                showCreateAccountUi(1, 1);
                return;
            case 1:
                if (valueOrPlaceholder(createUsernameInput, "").trim().isEmpty()) {
                    setAuthStatus("Choose a username.");
                    return;
                }
                showCreateAccountUi(2, 1);
                return;
            case 2:
                validateCreateEmail();
                return;
            case 3:
                if (valueOrPlaceholder(createPasswordInput, "").length() < 6) {
                    setAuthStatus("Password should be at least 6 characters.");
                    return;
                }
                showCreateAccountUi(4, 1);
                return;
            default:
                showCreateAccountUi(Math.min(step + 1, 5), 1);
        }
    }

    private void validateCreateEmail() {
        String email = valueOrPlaceholder(createEmailInput, "").trim();
        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            setAuthStatus("Enter a valid email address.");
            return;
        }

        setAuthStatus("Checking email...");
        runAuthRequest(() -> {
            if (isEmailTaken(email)) {
                runOnUiThread(() -> showExistingEmailDialog(email));
                return;
            }
            runOnUiThread(() -> showCreateAccountUi(3, 1));
        });
    }

    private boolean isEmailTaken(String email) throws Exception {
        Boolean indexed = isEmailIndexed(email);
        if (indexed != null) {
            return indexed;
        }

        String idToken = SecureTokenStore.get(nebulaAuthPrefs(), "idToken");
        if (idToken != null && !idToken.isEmpty() && firebaseLookupEmailExists(email, idToken)) {
            return true;
        }

        JSONObject payload = new JSONObject();
        payload.put("identifier", email);
        payload.put("continueUri", "https://nebula-faa46.firebaseapp.com");
        JSONObject result = firebaseAuthPost("accounts:createAuthUri", payload);
        JSONArray providers = result.optJSONArray("allProviders");
        JSONArray signInMethods = result.optJSONArray("signinMethods");
        if (result.optBoolean("registered", false)
                || (providers != null && providers.length() > 0)
                || (signInMethods != null && signInMethods.length() > 0)) {
            return true;
        }

        JSONObject probe = new JSONObject();
        probe.put("email", email);
        probe.put("password", "NebulaDefinitelyWrongPassword-000000");
        probe.put("returnSecureToken", true);
        try {
            firebaseAuthPost("accounts:signInWithPassword", probe);
            return true;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                return false;
            }
            if (message.contains("INVALID_PASSWORD")) {
                return true;
            }
            if (message.contains("EMAIL_NOT_FOUND") || message.contains("INVALID_LOGIN_CREDENTIALS")) {
                return false;
            }
            throw e;
        }
    }

    private Boolean isEmailIndexed(String email) {
        try {
            firestoreGet("emailIndex/" + emailIndexDocId(email), null);
            return true;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("NOT_FOUND")) {
                return false;
            }
            Log.w(TAG, "Nebula email index check failed", e);
            return null;
        }
    }

    private String emailIndexDocId(String email) throws Exception {
        return urlPath(email.trim().toLowerCase(Locale.US));
    }

    private boolean firebaseLookupEmailExists(String email, String idToken) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("idToken", idToken);
        payload.put("email", new JSONArray().put(email));
        JSONObject result = firebaseAuthPost("accounts:lookup", payload);
        JSONArray users = result.optJSONArray("users");
        return users != null && users.length() > 0;
    }

    private void showExistingEmailDialog(String email) {
        setAuthStatus("");
        AlertDialog dialog = new NebulaDialogBuilder(this).create();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(18));
        card.setBackground(roundedColor(0xF01B1F2C, dp(24), 0x554F7CFF, dp(1)));

        TextView title = text("Account found", 22, 0xFFFFFFFF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        TextView message = text("An account with this email already exists, would you like to use it?", 14, 0xFFAAB4E8, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        Button cancel = secondaryAuthButton("Cancel");
        Button use = primaryAuthButton("Yes");
        cancel.setOnClickListener(v -> dialog.dismiss());
        use.setOnClickListener(v -> {
            dialog.dismiss();
            showLoginUi(email);
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        useParams.leftMargin = dp(10);
        actions.addView(use, useParams);

        card.addView(title);
        card.addView(withTopMargin(message, dp(10)));
        card.addView(withTopMargin(actions, dp(18)));
        dialog.setView(card);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void startPendingEmailVerificationCheck() {
        if (pendingVerificationIdToken == null || pendingVerificationIdToken.isEmpty()) {
            showLoginUi(pendingVerificationEmail);
            return;
        }

        verificationPolling = true;
        startAuthLoading("Waiting for verification");
        pollPendingEmailVerification();
    }

    private void pollPendingEmailVerification() {
        if (!verificationPolling) {
            stopAuthLoading();
            return;
        }

        runAuthRequest(() -> {
            JSONObject lookup = new JSONObject();
            lookup.put("idToken", pendingVerificationIdToken);
            JSONObject lookupResult = firebaseAuthPost("accounts:lookup", lookup);
            JSONArray users = lookupResult.optJSONArray("users");
            boolean verified = users != null && users.length() > 0 && users.getJSONObject(0).optBoolean("emailVerified", false);
            if (!verified) {
                runOnUiThread(() -> {
                    if (verificationPolling) {
                        authUiHandler.postDelayed(this::pollPendingEmailVerification, 1000);
                    }
                });
                return;
            }

            String city = fetchUserCity();
            activatePendingNebulaAccount(city);
            JSONObject serverSession = createServerSession(pendingVerificationIdToken);
            runOnUiThread(() -> {
                verificationPolling = false;
                stopAuthLoading();
                saveNebulaSession(
                        pendingVerificationUid,
                        pendingVerificationEmail,
                        pendingVerificationUsername,
                        pendingVerificationName,
                        city,
                        pendingVerificationIdToken,
                        pendingVerificationRefreshToken,
                        true,
                        serverSession.optString("sessionToken", ""),
                        serverSession.optLong("expiresAt", 0L));
                Toast.makeText(this, "Account verified.", Toast.LENGTH_SHORT).show();
                resetAuthCreateFields();
                showLauncherUiWithSupernovaReveal();
                promptForStorageAccessAfterLogin();
            });
        });
    }

    private void resendPendingVerificationEmail() {
        if (pendingVerificationIdToken == null || pendingVerificationIdToken.isEmpty()) {
            showLoginUi(pendingVerificationEmail);
            return;
        }

        setAuthStatus("Sending verification email...");
        runAuthRequest(() -> {
            JSONObject verify = new JSONObject();
            verify.put("requestType", "VERIFY_EMAIL");
            verify.put("idToken", pendingVerificationIdToken);
            firebaseAuthPost("accounts:sendOobCode", verify);
            runOnUiThread(() -> setAuthStatus("Verification email sent."));
        });
    }

    private void activatePendingNebulaAccount(String city) throws Exception {
        if (pendingVerificationUid == null || pendingVerificationUid.isEmpty()) {
            return;
        }
        JSONObject activation = apiPost("/auth/activate",
                new JSONObject().put("city", city), pendingVerificationIdToken);
        requireApiSuccess(activation);
    }

    private void clearPendingVerification() {
        pendingVerificationUid = null;
        pendingVerificationEmail = null;
        pendingVerificationUsername = null;
        pendingVerificationName = null;
        pendingVerificationIdToken = null;
        pendingVerificationRefreshToken = null;
    }

    private void loginNebulaAccount(String email, String password) {
        email = email.trim();
        if (email.isEmpty() || password.isEmpty()) {
            setAuthStatus("Enter your email and password.");
            return;
        }

        String finalEmail = email;
        long loginStartedAt = System.currentTimeMillis();
        showAuthLoadingUi("Logging in", "Checking your Nebula account", 0xFF8B5CF6);
        startAuthLoading("Logging in");
        runLoginRequest(finalEmail, password, () -> {
            JSONObject login = new JSONObject();
            login.put("email", finalEmail);
            login.put("password", password);
            login.put("returnSecureToken", true);
            JSONObject authResult = firebaseAuthPost("accounts:signInWithPassword", login);
            String uid = authResult.getString("localId");
            String idToken = authResult.getString("idToken");
            String refreshToken = authResult.optString("refreshToken", "");
            String city = fetchUserCity();

            JSONObject lookup = new JSONObject();
            lookup.put("idToken", idToken);
            JSONObject lookupResult = firebaseAuthPost("accounts:lookup", lookup);
            JSONArray users = lookupResult.optJSONArray("users");
            boolean verified = users != null && users.length() > 0 && users.getJSONObject(0).optBoolean("emailVerified", false);
            if (!verified) {
                throw new Exception("Please verify your email first");
            }

            JSONObject fields = getUserFieldsForLogin(uid, finalEmail, idToken);
            if (fields == null) {
                throw new Exception("Nebula account profile missing");
            }
            if (!isAccountActive(fields)) {
                throw new Exception("this account has been deactivated, contact support");
            }

            boolean activated = firestoreBool(fields, "activated");
            if (!activated) {
                JSONObject activation = apiPost("/auth/activate",
                        new JSONObject().put("city", city), idToken);
                requireApiSuccess(activation);
                activated = true;
            }

            String username = firestoreString(fields, "username");
            String name = firestoreString(fields, "name");
            boolean finalActivated = activated;
            JSONObject serverSession = createServerSession(idToken);
            runOnUiThread(() -> {
                authUiHandler.post(() -> {
                    stopAuthLoading();
                    saveNebulaSession(uid, finalEmail, username, name, city, idToken, refreshToken,
                            finalActivated, serverSession.optString("sessionToken", ""),
                            serverSession.optLong("expiresAt", 0L));
                    Toast.makeText(this, "Logged in successfully.", Toast.LENGTH_SHORT).show();
                    showLauncherUiWithSupernovaReveal();
                    promptForStorageAccessAfterLogin();
                });
            });
        });
    }

    private void promptForStorageAccessAfterLogin() {
        // App-private runtime storage does not require a broad storage permission.
    }

    private void runLoginRequest(String email, String password, AuthWork work) {
        new Thread(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Log.w(TAG, "Nebula login failed", e);
                String message = readableAuthError(e);
                if (message.equals("Email or password was incorrect.")) {
                    runOnUiThread(() -> promptGoogleAfterPasswordAttempt(email, password));
                    return;
                }
                runOnUiThread(() -> {
                    verificationPolling = false;
                    stopAuthLoading();
                    showLoginUi(email);
                    setAuthStatus(message);
                });
            }
        }, "NebulaLogin").start();
    }

    private void promptGoogleAfterPasswordAttempt(String email, String password) {
        verificationPolling = false;
        stopAuthLoading();
        showLoginUi(email);
        new NebulaDialogBuilder(this)
                .setTitle("Could not sign in with password")
                .setMessage("Retry your password, or authenticate with Google. Linking options are shown only after Google and the Nebula server verify a matching Google-only account.")
                .setPositiveButton("Continue with Google", (dialog, which) -> {
                    pendingPasswordLinkEmail = email.trim().toLowerCase(Locale.US);
                    pendingPasswordLinkPassword = password;
                    beginGoogleSignIn();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String providerState(String idToken) throws Exception {
        JSONObject response = apiPost("/auth/provider-state", new JSONObject(), idToken);
        requireApiSuccess(response);
        return response.optString("state", "none");
    }

    private JSONObject getUserFieldsForLogin(String uid, String email, String idToken) throws Exception {
        JSONObject response = apiPost("/auth/profile", new JSONObject(), idToken);
        requireApiSuccess(response);
        return response.optBoolean("found", false) ? response.optJSONObject("fields") : null;
    }

    private boolean hasProfileName(JSONObject fields) {
        if (fields == null) {
            return false;
        }
        return !firestoreString(fields, "name").isEmpty() && !firestoreString(fields, "username").isEmpty();
    }

    private boolean isAccountActive(JSONObject fields) {
        if (fields == null) {
            return true;
        }
        JSONObject value = fields.optJSONObject("active");
        return value == null || value.optBoolean("booleanValue", true);
    }

    private void sendNebulaPasswordReset(String email) {
        email = email.trim();
        if (email.isEmpty()) {
            setAuthStatus("Enter your email first.");
            return;
        }

        String finalEmail = email;
        setAuthStatus("Sending reset email...");
        runAuthRequest(() -> {
            JSONObject reset = new JSONObject();
            reset.put("requestType", "PASSWORD_RESET");
            reset.put("email", finalEmail);
            firebaseAuthPost("accounts:sendOobCode", reset);
            runOnUiThread(() -> setAuthStatus("Password reset email sent."));
        });
    }

    private void saveNebulaSession(String uid, String email, String username, String name, String city,
            String idToken, String refreshToken, boolean activated, String serverSessionToken,
            long serverSessionExpiresAt) {
        nebulaAuthPrefs().edit()
                .putBoolean("loggedIn", true)
                .putString("uid", uid)
                .putString("email", email)
                .putString("username", username)
                .putString("name", name)
                .putBoolean("activated", activated)
                .putLong("serverSessionExpiresAt", serverSessionExpiresAt)
                .apply();
        SecureTokenStore.put(nebulaAuthPrefs(), "idToken", idToken);
        SecureTokenStore.put(nebulaAuthPrefs(), "refreshToken", refreshToken);
        SecureTokenStore.put(nebulaAuthPrefs(), "serverSessionToken", serverSessionToken);
        if (activated && serverSessionToken != null && !serverSessionToken.isEmpty()) {
            new Thread(() -> {
                try {
                    NebulaCompatManager.ensureInstalled(this, serverSessionToken);
                } catch (Exception error) {
                    Log.w(TAG, "Could not download NebulaCompat after login", error);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Compatibility support could not be downloaded yet. Nebula will retry automatically.",
                            Toast.LENGTH_LONG).show());
                }
            }, "NebulaCompatDownload").start();
        }
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
        String serverSessionToken = SecureTokenStore.get(nebulaAuthPrefs(), "serverSessionToken");
        if (serverSessionToken == null || serverSessionToken.isEmpty()) {
            finishLocalLogout();
            return;
        }

        showBlockingLoading("Logging out", "Deleting this device session from Nebula\u2026");
        new Thread(() -> {
            try {
                NebulaSessionApi.revoke(serverSessionToken);
                runOnUiThread(this::finishLocalLogout);
            } catch (Exception e) {
                Log.w(TAG, "Could not revoke the Nebula server session during logout", e);
                runOnUiThread(() -> {
                    hideBlockingLoading();
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

    private void finishLocalLogout() {
        hideBlockingLoading();
        if (firebaseAuth != null) firebaseAuth.signOut();
        if (credentialManager != null) {
            credentialManager.clearCredentialStateAsync(
                    new androidx.credentials.ClearCredentialStateRequest(),
                    new android.os.CancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                    new CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                        @Override public void onResult(Void ignored) { }
                        @Override public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException error) {
                            Log.w(TAG, "Could not clear Google credential state", error);
                        }
                    });
        }
        nebulaAuthPrefs().edit().clear().apply();
        Toast.makeText(this, "Logged out of Nebula.", Toast.LENGTH_SHORT).show();
        resetAuthCreateFields();
        showWelcomeUi();
    }

    private void resetAuthCreateFields() {
        verificationPolling = false;
        stopAuthLoading();
        createNameInput = null;
        createUsernameInput = null;
        createEmailInput = null;
        createPasswordInput = null;
        authStatusView = null;
        createAccountStep = 0;
        clearPendingVerification();
    }

    private void runAuthRequest(AuthWork work) {
        new Thread(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Log.w(TAG, "Nebula account request failed", e);
                runOnUiThread(() -> {
                    verificationPolling = false;
                    stopAuthLoading();
                    String message = e.getMessage();
                    if ("EMAIL_EXISTS".equals(message) && createEmailInput != null) {
                        showExistingEmailDialog(valueOrPlaceholder(createEmailInput, ""));
                        return;
                    }
                    setAuthStatus(readableAuthError(e));
                });
            }
        }, "NebulaAuth").start();
    }

    private JSONObject firebaseAuthPost(String method, JSONObject payload) throws Exception {
        return requestJson("POST", FIREBASE_AUTH_BASE + method + "?key=" + firebaseApiKey(), payload, null);
    }

    private void ensureServerSession(SharedPreferences prefs, String firebaseIdToken) throws Exception {
        String sessionToken = SecureTokenStore.get(prefs, "serverSessionToken");
        if (sessionToken != null && !sessionToken.isEmpty()) {
            JSONObject check = apiPost("/auth/session/check",
                    new JSONObject().put("sessionToken", sessionToken), null);
            if (check.optBoolean("success", false)) {
                JSONObject refresh = apiPost("/auth/session/refresh",
                        new JSONObject().put("sessionToken", sessionToken), null);
                if (refresh.optBoolean("success", false)
                        && !refresh.optString("sessionToken", "").isEmpty()) {
                    SecureTokenStore.put(prefs, "serverSessionToken", refresh.getString("sessionToken"));
                    prefs.edit().putLong("serverSessionExpiresAt", refresh.optLong("expiresAt", 0L)).apply();
                    Log.i(TAG, "Refreshed the Nebula API session for this app launch.");
                    return;
                }
                Log.w(TAG, "Nebula API session refresh failed; creating a replacement session.");
            }
            Log.i(TAG, "Saved Nebula API session is no longer valid; creating a replacement.");
        }

        JSONObject replacement = createServerSession(firebaseIdToken);
        SecureTokenStore.put(prefs, "serverSessionToken", replacement.getString("sessionToken"));
        prefs.edit().putLong("serverSessionExpiresAt", replacement.optLong("expiresAt", 0L)).apply();
    }

    private JSONObject createServerSession(String firebaseIdToken) throws Exception {
        JSONObject accountCheck = apiPost("/auth/check", new JSONObject(), firebaseIdToken);
        requireApiSuccess(accountCheck);

        JSONObject payload = new JSONObject()
                .put("deviceId", getNebulaDeviceId())
                .put("deviceName", getNebulaDeviceName());
        JSONObject session = apiPost("/auth/session", payload, firebaseIdToken);
        requireApiSuccess(session);
        if (session.optString("sessionToken", "").isEmpty()) {
            throw new IOException("Nebula API did not return a session token");
        }
        return session;
    }

    private JSONObject apiPost(String path, JSONObject payload, String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(NEBULA_API_BASE + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.0 Android");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readStream(stream);
        if (body.isEmpty()) {
            throw new IOException("Nebula API returned HTTP " + code + " without a response");
        }
        return new JSONObject(body);
    }

    private void requireApiSuccess(JSONObject response) throws AccountRejectedException {
        if (!response.optBoolean("success", false)) {
            String error = response.optString("error", "Nebula authentication failed");
            if ("DEVICE_LIMIT_REACHED".equals(error)) {
                error = "This account already has the maximum of 3 authorized devices.";
            }
            throw new AccountRejectedException(error);
        }
    }

    private String getNebulaDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "android:" + (androidId == null || androidId.isEmpty() ? "unknown" : androidId);
    }

    private String getNebulaDeviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "Android device" : Build.MODEL.trim();
        if (manufacturer.isEmpty() || model.toLowerCase(Locale.US).startsWith(manufacturer.toLowerCase(Locale.US))) {
            return model;
        }
        return manufacturer + " " + model;
    }

    private static final class AccountRejectedException extends Exception {
        AccountRejectedException(String message) {
            super(message);
        }
    }

    private JSONObject firebaseRefreshIdToken(String refreshToken) throws Exception {
        String body = "grant_type=refresh_token&refresh_token=" + urlQuery(refreshToken);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://securetoken.googleapis.com/v1/token?key=" + firebaseApiKey()).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.0 Android");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readStream(stream);
        if (code < 200 || code >= 300) {
            throw new Exception(parseFirebaseError(response, code));
        }
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private JSONObject firestoreGet(String path, String idToken) throws Exception {
        return requestJson("GET", firestoreBase() + path, null, idToken);
    }

    private JSONObject requestJson(String method, String urlText, JSONObject payload, String idToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NebulaLauncher/1.0 Android");
        if (idToken != null && !idToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + idToken);
        }
        if (payload != null) {
            connection.setDoOutput(true);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readStream(stream);
        if (code < 200 || code >= 300) {
            throw new Exception(parseFirebaseError(body, code));
        }
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private String fetchUserCity() {
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String parseFirebaseError(String body, int code) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
        }
        return "Request failed (" + code + ")";
    }

    private String readableAuthError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isEmpty()) {
            return "Something went wrong.";
        }
        if (message.contains("Invalid activation key") || message.contains("NOT_FOUND")) {
            return "Invalid activation key";
        }
        if (message.contains("Activation key already used")) {
            return "Activation key already used.";
        }
        if (message.contains("That email already has an account")) {
            return "That email already has an account.";
        }
        if (message.contains(DEACTIVATED_MESSAGE)) {
            return DEACTIVATED_MESSAGE;
        }
        if (message.contains("Could not check that email")) {
            return "Could not check that email. Try again.";
        }
        switch (message) {
            case "EMAIL_EXISTS":
                return "That email already has an account.";
            case "EMAIL_NOT_FOUND":
            case "INVALID_LOGIN_CREDENTIALS":
            case "INVALID_PASSWORD":
                return "Email or password was incorrect.";
            case "INVALID_EMAIL":
                return "Enter a valid email address.";
            case "WEAK_PASSWORD : Password should be at least 6 characters":
                return "Password should be at least 6 characters.";
            default:
                return message.replace('_', ' ');
        }
    }

    private void setAuthStatus(String message) {
        if (authStatusView != null) {
            authStatusView.setText(message);
        }
    }

    private void showAuthLoadingUi(String titleText, String subtitleText, int accentColor) {
        stopAuthLoading();
        LinearLayout content = authShell();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(36), dp(28), dp(36));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nebula_logo_rounded);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo, centeredParams(dp(86), dp(86), dp(14)));

        View wordmark = nebulaWordmark(30, 0xFFFFFFFF, accentColor, Gravity.CENTER);
        content.addView(wordmark);

        TextView title = text(titleText, 23, 0xFFFFFFFF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(withTopMargin(title, dp(18)));

        TextView subtitle = text(subtitleText, 14, 0xFFAAB4E8, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(withTopMargin(subtitle, dp(8)));

        content.addView(withTopMargin(authLoadingSpinner(accentColor), dp(24)));
        authStatusView = authStatus();
        content.addView(withTopMargin(authStatusView, dp(14)));

        setContentView(authRoot(content));
    }

    private View authLoadingSpinner(int accentColor) {
        NebulaLoaderView loader = new NebulaLoaderView(this);
        loader.setAccentColor(accentColor);
        loader.setTag("nebula-auth-loader");
        loader.setLayoutParams(new LinearLayout.LayoutParams(dp(116), dp(116)));
        return loader;
    }

    private String firebaseApiKey() {
        return getString(R.string.google_api_key);
    }

    private String firestoreBase() {
        return "https://firestore.googleapis.com/v1/projects/"
                + getString(R.string.project_id)
                + "/databases/(default)/documents/";
    }

    private void startAuthLoading(String label) {
        stopAuthLoading();
        final int[] tick = { 0 };
        authLoadingRunnable = new Runnable() {
            @Override
            public void run() {
                int dots = tick[0] % 4;
                StringBuilder text = new StringBuilder(label);
                for (int i = 0; i < dots; i++) {
                    text.append('.');
                }
                setAuthStatus(text.toString());
                tick[0]++;
                authUiHandler.postDelayed(this, 360);
            }
        };
        authLoadingRunnable.run();
    }

    private void stopAuthLoading() {
        if (authLoadingRunnable != null) {
            authUiHandler.removeCallbacks(authLoadingRunnable);
            authLoadingRunnable = null;
        }
    }

    private String firestoreString(JSONObject fields, String key) {
        JSONObject value = fields.optJSONObject(key);
        return value == null ? "" : value.optString("stringValue", "");
    }

    private boolean firestoreBool(JSONObject fields, String key) {
        JSONObject value = fields.optJSONObject(key);
        return value != null && value.optBoolean("booleanValue", false);
    }

    private String urlPath(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private String urlQuery(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private interface AuthWork {
        void run() throws Exception;
    }

    private View createLauncherView() {
        FrameLayout root = new FrameLayout(this);
        root.setTag("nebula-launcher-root");
        root.setBackground(roundedGradient(0xFF0B1024, 0xFF151A3A, 0));

        View glowTop = new View(this);
        glowTop.setAlpha(0.55f);
        glowTop.setBackground(roundedGradient(0x808B5CF6, 0x0022D3EE, dp(140)));
        FrameLayout.LayoutParams glowTopParams = new FrameLayout.LayoutParams(dp(280), dp(280));
        glowTopParams.gravity = Gravity.TOP | Gravity.RIGHT;
        glowTopParams.topMargin = -dp(90);
        glowTopParams.rightMargin = -dp(100);
        root.addView(glowTop, glowTopParams);

        View glowBottom = new View(this);
        glowBottom.setAlpha(0.42f);
        glowBottom.setBackground(roundedGradient(0x804FD1C5, 0x000B1024, dp(130)));
        FrameLayout.LayoutParams glowBottomParams = new FrameLayout.LayoutParams(dp(260), dp(260));
        glowBottomParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        glowBottomParams.leftMargin = -dp(120);
        glowBottomParams.bottomMargin = -dp(100);
        root.addView(glowBottom, glowBottomParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int padding = dp(18);
        scrollView.setPadding(padding, padding, padding, padding);
        Utilities.applyWindowInsets(scrollView, padding);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(createHeader());
        content.addView(createWelcomeCard());
        if (isWideLayout()) {
            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            grid.setGravity(Gravity.TOP);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(createLaunchCard());
            left.addView(createLauncherActionGrid());
            left.addView(createErrorCard());

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.addView(createModsCard());

            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            leftParams.rightMargin = dp(14);
            grid.addView(left, leftParams);
            grid.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            content.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        } else {
            content.addView(createLaunchCard());
            content.addView(createLauncherActionGrid());
            content.addView(createModsCard());
            content.addView(createErrorCard());
        }

        root.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        animateStaggeredChildren(content, 46);

        root.setAlpha(0f);
        root.setTranslationY(dp(18));
        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        return root;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        FrameLayout content = findViewById(android.R.id.content);
        boolean showingLauncher = content != null
                && content.findViewWithTag("nebula-launcher-root") != null;
        super.onConfigurationChanged(newConfig);

        // The launcher chooses a one- or two-column hierarchy when it is
        // created. Android can resize the existing views, but it cannot turn
        // that hierarchy into the other form after a rotation. Rebuild only
        // the signed-in launcher; auth screens remain intact so rotating does
        // not discard an in-progress account flow.
        if (showingLauncher) {
            showLauncherUi();
        }
    }

    private void maybePromptForGameZip() {
        if (gameImportPromptShown || isAmongUsInstalled()) {
            return;
        }
        gameImportPromptShown = true;
        new NebulaDialogBuilder(this)
                .setTitle("Install Among Us")
                .setMessage("Nebula now uses the native Android version of Among Us. Install it from Google Play, then return here to launch with FusionCore.")
                .setPositiveButton("Open Google Play", (dialog, which) -> openAmongUsStore())
                .setNegativeButton("Later", null)
                .show();
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(14));

        LinearLayout branding = new LinearLayout(this);
        branding.setOrientation(LinearLayout.HORIZONTAL);
        branding.setGravity(Gravity.CENTER_VERTICAL);
        branding.addView(nebulaWordmark(
                28, 0xFFF0F3FF, 0xFF22D3EE, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        if (BuildConfig.DEBUG_MODE) {
            TextView debug = pill("DEBUG", 0x33F59E0B, 0xFFFFC66D);
            debug.setTextSize(10);
            LinearLayout.LayoutParams debugParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            debugParams.leftMargin = dp(9);
            branding.addView(debug, debugParams);
        }
        header.addView(branding, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = pill(BuildConfig.VERSION_NAME, 0x3322D3EE, 0xFF9AE6FF);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        badgeParams.rightMargin = dp(10);
        header.addView(badge, badgeParams);

        ImageButton settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setScaleType(ImageView.ScaleType.CENTER);
        settingsButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        settingsButton.setBackground(roundedColor(0xDD252D4B, dp(18), 0x334F7CFF, dp(1)));
        settingsButton.setContentDescription(getString(R.string.selector_action_settings));
        settingsButton.setOnClickListener(v -> UiMotion.open(this, new Intent(this, SettingsActivity.class)));
        addTapScale(settingsButton);
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return header;
    }

    private View createWelcomeCard() {
        LinearLayout card = card(0xB8202847, 0x334F7CFF);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView greeting = text("Welcome,", 14, 0xFF8790B9, Typeface.NORMAL);
        TextView user = text(displayNebulaName(), 30, 0xFFFFFFFF, Typeface.BOLD);
        targetStatusView = text("Checking Among Us install...", 12, 0xFF9AA7D6, Typeface.NORMAL);

        card.addView(greeting);
        card.addView(user);
        card.addView(targetStatusView);
        return withBottomMargin(card, dp(14));
    }

    private String displayNebulaName() {
        SharedPreferences prefs = nebulaAuthPrefs();
        String name = prefs.getString("name", "");
        String firstName = firstDisplayName(name);
        if (!firstName.isEmpty()) {
            return firstName;
        }
        String username = prefs.getString("username", "");
        firstName = firstDisplayName(username);
        if (!firstName.isEmpty()) {
            return firstName;
        }
        String email = prefs.getString("email", "");
        if (email != null && !email.trim().isEmpty()) {
            String localPart = email.trim().split("@")[0];
            firstName = firstDisplayName(localPart.replace('.', ' ').replace('_', ' '));
            if (!firstName.isEmpty()) {
                return firstName;
            }
        }
        return "Pilot";
    }

    private String firstDisplayName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.split("\\s+")[0];
    }

    private View createLaunchCard() {
        LinearLayout card = card(0xD51B2443, 0x406FCF97);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button launchButton = new Button(this);
        launchButton.setAllCaps(false);
        launchButton.setText("Launch");
        launchButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_nebula_play, 0, 0, 0);
        launchButton.setCompoundDrawablePadding(dp(8));
        launchButton.setTextColor(Color.WHITE);
        launchButton.setTextSize(20);
        launchButton.setTypeface(Typeface.DEFAULT_BOLD);
        launchButton.setBackground(roundedGradient(0xFF52D273, 0xFF22D3EE, dp(20)));
        launchButton.setOnClickListener(v -> {
            if (!isAmongUsInstalled()) {
                openAmongUsStore();
                return;
            }
            maybeLaunchBootstrap("com.innersloth.spacemafia");
        });
        addTapScale(launchButton);
        row.addView(launchButton, new LinearLayout.LayoutParams(0, dp(64), 1f));

        modsCountView = text("0 mods", 15, 0xFFEAF2FF, Typeface.BOLD);
        modsCountView.setGravity(Gravity.CENTER);
        modsCountView.setBackground(roundedColor(0x5522D3EE, dp(18), 0x5538BDF8, dp(1)));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(dp(92), dp(58));
        countParams.leftMargin = dp(12);
        row.addView(modsCountView, countParams);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.setPadding(0, dp(12), 0, 0);
        launchStatusView = text("ready", 12, 0xFF6FCF97, Typeface.BOLD);
        storageStatusView = text("Storage ready", 12, 0xFF8790B9, Typeface.NORMAL);
        subRow.addView(launchStatusView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        subRow.addView(storageStatusView);

        card.addView(row);
        card.addView(subRow);
        return withBottomMargin(card, dp(14));
    }

    private View createLauncherActionGrid() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(actionButton("Mod Store", "browse catalog", "M", 0xFF6366F1,
                v -> UiMotion.open(this, new Intent(this, ModStoreActivity.class))), weightedActionParams(false));
        top.addView(actionButton("Profiles", ProfileManager.getActiveName(this), "P", 0xFFFF8A3D,
                v -> UiMotion.open(this, new Intent(this, ProfilesActivity.class))), weightedActionParams(true));
        wrap.addView(top);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(0, dp(10), 0, 0);
        bottom.addView(actionButton("Browse Files", "Nebula", "F", 0xFF22D3EE, v -> openFileBrowser()), weightedActionParams(false));
        bottom.addView(actionButton("Check for updates", "app & mods", "U", 0xFFF472B6,
                v -> UiMotion.open(this, new Intent(this, UpdatesActivity.class))), weightedActionParams(true));
        wrap.addView(bottom);

        return withBottomMargin(wrap, dp(14));
    }

    private View createActionGrid() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(actionButton("Mod Store", "browse catalog", "M", 0xFF6366F1,
                v -> UiMotion.open(this, new Intent(this, ModStoreActivity.class))), weightedActionParams(false));
        top.addView(actionButton("Profiles", ProfileManager.getActiveName(this), "P", 0xFFFF8A3D,
                v -> UiMotion.open(this, new Intent(this, ProfilesActivity.class))), weightedActionParams(true));
        wrap.addView(top);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(0, dp(10), 0, 0);
        bottom.addView(actionButton("Account Link", "import", "●", 0xFF6FCF97, v -> showAccountLinkDialog()), weightedActionParams(false));
        bottom.addView(actionButton("Browse Files", "Nebula", "F", 0xFF22D3EE, v -> openFileBrowser()), weightedActionParams(true));
        wrap.addView(bottom);

        LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.HORIZONTAL);
        extra.setPadding(0, dp(10), 0, 0);
        extra.addView(actionButton("Check for updates", "app & mods", "U", 0xFFF472B6,
                v -> UiMotion.open(this, new Intent(this, UpdatesActivity.class))), weightedActionParams(false));
        extra.addView(actionButton("Log Out", "account", "↩", 0xFFF28B82, v -> confirmLogout()), weightedActionParams(true));
        wrap.addView(extra);

        return withBottomMargin(wrap, dp(14));
    }

    private View createModsCard() {
        LinearLayout card = card(0xD51B2443, 0x334F7CFF);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Profile mods", 17, 0xFFF0F3FF, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = smallIconButton("R");
        refresh.setText("Refresh");
        refresh.setTextSize(12);
        refresh.setOnClickListener(v -> refreshModsUi());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(38)));
        card.addView(header);

        modsPathView = text("Preparing shared mod folder...", 11, 0xFF8790B9, Typeface.NORMAL);
        modsPathView.setPadding(0, dp(8), 0, dp(8));
        card.addView(modsPathView);

        modsListView = new ListView(this);
        modsListView.setDivider(null);
        modsListView.setDividerHeight(dp(8));
        modsListView.setCacheColorHint(Color.TRANSPARENT);
        modsListView.setBackgroundColor(Color.TRANSPARENT);
        modsListView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        modsListView.setNestedScrollingEnabled(true);
        card.addView(modsListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                isWideLayout() ? dp(390) : dp(260)
        ));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(10), 0, 0);
        deleteAllModsButton = darkButton("Delete Mods");
        deleteAllModsButton.setOnClickListener(v -> confirmDeleteAllMods());
        tools.addView(deleteAllModsButton, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button browse = darkButton("Files");
        browse.setOnClickListener(v -> openFileBrowser());
        LinearLayout.LayoutParams browseParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        browseParams.leftMargin = dp(10);
        tools.addView(browse, browseParams);
        card.addView(tools);

        return withBottomMargin(card, dp(14));
    }

    private View createErrorCard() {
        errorCardView = card(0xAA3A1B28, 0x55F28B82);
        errorCardView.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView title = text("An error occurred", 16, 0xFFF0F3FF, Typeface.BOLD);
        errorCardView.addView(title);

        lastErrorView = text("", 12, 0xFFFFC2C2, Typeface.NORMAL);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(6);
        errorCardView.addView(lastErrorView, messageParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        seeMoreLastErrorButton = darkButton("See more");
        seeMoreLastErrorButton.setOnClickListener(v -> showLastErrorDetails());
        Button reportErrorButton = darkButton("Report bug");
        reportErrorButton.setOnClickListener(v -> startActivity(new Intent(this, BugReportActivity.class)
                .putExtra(BugReportActivity.EXTRA_ERROR, lastErrorFullText)));
        clearLastErrorButton = darkButton("Clear");
        clearLastErrorButton.setOnClickListener(v -> clearLastError());

        LinearLayout.LayoutParams seeMoreParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        buttons.addView(seeMoreLastErrorButton, seeMoreParams);
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        reportParams.leftMargin = dp(8);
        buttons.addView(reportErrorButton, reportParams);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        clearParams.leftMargin = dp(8);
        buttons.addView(clearLastErrorButton, clearParams);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(8);
        errorCardView.addView(buttons, buttonParams);
        errorCardView.setVisibility(View.GONE);
        return errorCardView;
    }

    private void showProfiles() {
        List<String> profiles = ProfileManager.list(this);
        String active = ProfileManager.getActiveName(this);
        String[] names = profiles.toArray(new String[0]);
        int selected = Math.max(0, profiles.indexOf(active));
        AlertDialog dialog = new NebulaDialogBuilder(this)
                .setTitle("Profiles")
                .setSingleChoiceItems(names, selected, (choice, which) -> {
                    try {
                        ProfileManager.activate(this, names[which]);
                        choice.dismiss();
                        recreate();
                    } catch (IOException e) {
                        Toast.makeText(this, "Could not activate profile.", Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton("New profile", null)
                .setNeutralButton("Delete active", null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> showCreateProfile(dialog));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    if (!ProfileManager.delete(this, ProfileManager.getActiveName(this))) {
                        Toast.makeText(this, "The Default profile cannot be deleted.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    recreate();
                } catch (IOException e) {
                    Toast.makeText(this, "Could not delete profile.", Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private void showCreateProfile(AlertDialog parent) {
        EditText input = new EditText(this);
        input.setHint("Profile name");
        input.setSingleLine(true);
        new NebulaDialogBuilder(this)
                .setTitle("New profile")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    if (!ProfileManager.create(this, input.getText().toString())) {
                        Toast.makeText(this, "Choose a unique profile name.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        ProfileManager.activate(this, input.getText().toString());
                        parent.dismiss();
                        recreate();
                    } catch (IOException e) {
                        Toast.makeText(this, "Could not activate profile.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showModStore() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(8));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Mod Store", 22, 0xFF17213A, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button upload = darkButton("Upload");
        header.addView(upload, new LinearLayout.LayoutParams(dp(100), dp(44)));
        content.addView(header);
        TextView empty = text("The store is empty for now.\nUploaded .dll and .zip mods are added to "
                + ProfileManager.getActiveName(this) + ".", 14, 0xFF667085, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(60), 0, dp(60));
        content.addView(empty);
        AlertDialog dialog = new NebulaDialogBuilder(this)
                .setView(content)
                .setNegativeButton("Close", null)
                .create();
        upload.setOnClickListener(v -> {
            dialog.dismiss();
            beginImportMods();
        });
        dialog.show();
    }

    private LinearLayout card(int color, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedColor(color, dp(26), strokeColor, dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(7));
        }
        return card;
    }

    private Button actionButton(String title, String subtitle, String icon, int accent, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title + "\n" + subtitle);
        button.setTextColor(0xFFF0F3FF);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(0, actionIconForTitle(title), 0, 0);
        button.setCompoundDrawablePadding(dp(6));
        button.setBackground(roundedColor(0xCC202A48, dp(22), accent, dp(1)));
        button.setOnClickListener(listener);
        addTapScale(button);
        return button;
    }

    private int actionIconForTitle(String title) {
        if ("Upload Mod".equals(title) || "Mod Store".equals(title)) {
            return R.drawable.ic_nebula_upload;
        }
        if ("Profiles".equals(title)) {
            return R.drawable.ic_nebula_puzzle;
        }
        if ("Runtime".equals(title)) {
            return R.drawable.ic_nebula_rocket;
        }
        if ("Account Link".equals(title)) {
            return R.drawable.ic_nebula_group;
        }
        if ("Log Out".equals(title) || "Check for updates".equals(title)) {
            return R.drawable.ic_nebula_refresh;
        }
        return R.drawable.ic_nebula_folder;
    }

    private LinearLayout.LayoutParams weightedActionParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(104), 1f);
        if (withLeftMargin) {
            params.leftMargin = dp(10);
        }
        return params;
    }

    private Button darkButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(0xFFF0F3FF);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedColor(0xDD252D4B, dp(18), 0x334F7CFF, dp(1)));
        addTapScale(button);
        return button;
    }

    private Button smallIconButton(String text) {
        Button button = darkButton(text);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private TextView text(String value, int sp, int color, int typefaceStyle) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, typefaceStyle);
        view.setIncludeFontPadding(true);
        return view;
    }

    private View nebulaWordmark(int sp, int baseColor, int ulaColor, int gravity) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(gravity);

        Typeface face = orbitron();
        TextView neb = text("Neb", sp, baseColor, Typeface.BOLD);
        TextView ula = text("ula", sp, ulaColor, Typeface.BOLD);
        neb.setTypeface(face, Typeface.BOLD);
        ula.setTypeface(face, Typeface.BOLD);
        neb.getPaint().setFakeBoldText(true);
        ula.getPaint().setFakeBoldText(true);
        neb.setIncludeFontPadding(false);
        ula.setIncludeFontPadding(false);
        wrap.addView(neb);
        wrap.addView(ula);
        return wrap;
    }

    private Typeface orbitron() {
        if (orbitronTypeface == null) {
            try {
                orbitronTypeface = Typeface.createFromAsset(getAssets(), "fonts/Orbitron.ttf");
            } catch (Exception e) {
                Log.w(TAG, "Could not load Orbitron font", e);
                orbitronTypeface = Typeface.DEFAULT_BOLD;
            }
        }
        return orbitronTypeface;
    }

    private TextView pill(String value, int color, int textColor) {
        TextView view = text(value, 12, textColor, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(roundedColor(color, dp(18), 0x554F7CFF, dp(1)));
        return view;
    }

    private View withBottomMargin(View view, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable roundedGradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] { start, end }
        );
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedColor(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private void addTapScale(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.965f).scaleY(0.965f).alpha(0.86f).setDuration(85).start();
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            }
            return false;
        });
    }

    private void addFocusLift(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.015f : 1f;
            float translation = hasFocus ? -dp(2) : 0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationY(translation)
                    .setDuration(170)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        });
    }

    private void animateStaggeredChildren(ViewGroup parent, long delayStepMs) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(16));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * delayStepMs)
                    .setDuration(360)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void animateAmbientGlow(View view, float minAlpha, float maxAlpha, int driftX) {
        view.animate()
                .alpha(maxAlpha)
                .translationX(driftX)
                .translationY(-driftX)
                .setDuration(1400)
                .setStartDelay(180)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> view.animate()
                        .alpha(minAlpha)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(1600)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start())
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isWideLayout() {
        return getResources().getDisplayMetrics().widthPixels >= getResources().getDisplayMetrics().heightPixels
                && getResources().getDisplayMetrics().widthPixels >= dp(680);
    }

    private List<AppEntry> resolveInstalledTargets() {
        PackageManager pm = getPackageManager();
        List<AppEntry> result = new ArrayList<>();

        for (String pkg : SUPPORTED_PACKAGES) {
            if (pm.getLaunchIntentForPackage(pkg) == null) {
                continue;
            }

            String label = pkg;
            Drawable icon = pm.getDefaultActivityIcon();
            String versionName = "Unknown";
            long versionCode = 0L;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(info).toString();
                icon = pm.getApplicationIcon(info);

                PackageInfo packageInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(pkg, 0);
                }
                if (packageInfo.versionName != null && !packageInfo.versionName.isEmpty()) {
                    versionName = packageInfo.versionName;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    versionCode = packageInfo.getLongVersionCode();
                } else {
                    versionCode = packageInfo.versionCode;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resolve metadata for package: " + pkg, e);
            }

            result.add(new AppEntry(pkg, label, icon, versionName, versionCode));
        }

        return result;
    }

    private void launchBootstrap(String packageName) {
        try {
            FusionRuntimeManager.ensureModDirectories(this);
            ProfileManager.stageActive(this);
        } catch (IOException e) {
            launchRequestInFlight = false;
            Log.e(TAG, "Failed to stage the active profile before launch", e);
            new NebulaDialogBuilder(this)
                    .setTitle("Could not prepare mods")
                    .setMessage("Nebula could not stage the active Android mod profile. Check storage access and try again.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        Intent intent = new Intent(this, BootstrapActivity.class);
        intent.putExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }

    private void maybeLaunchBootstrap(String packageName) {
        if (!requireSupportedAmongUsVersion(packageName)) {
            return;
        }
        if (launchRequestInFlight) {
            Log.w(TAG, "Ignoring duplicate launch request while bootstrap is being prepared.");
            return;
        }
        launchRequestInFlight = true;
        showBlockingLoading("Preparing compatibility", "Checking Nebula compatibility support…");
        new Thread(() -> {
            try {
                NebulaCompatManager.ensureInstalled(this,
                        SecureTokenStore.get(nebulaAuthPrefs(), "serverSessionToken"));
                runOnUiThread(() -> {
                    launchRequestInFlight = false;
                    hideBlockingLoading();
                    launchBootstrap(packageName);
                });
            } catch (Exception error) {
                Log.w(TAG, "Could not prepare NebulaCompat for launch", error);
                runOnUiThread(() -> {
                    launchRequestInFlight = false;
                    hideBlockingLoading();
                    new NebulaDialogBuilder(this)
                            .setTitle("Compatibility download failed")
                            .setMessage(error.getMessage() == null
                                    ? "Nebula could not download compatibility support. Check your connection and try again."
                                    : error.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "NebulaCompatLaunchCheck").start();
    }

    private boolean requireSupportedAmongUsVersion(String packageName) {
        if (!GameCompatibility.AMONG_US_PACKAGE.equals(packageName)) {
            return true;
        }
        final PackageInfo packageInfo;
        try {
            packageInfo = GameCompatibility.getPackageInfo(this, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            new NebulaDialogBuilder(this)
                    .setTitle("Among Us is not installed")
                    .setMessage("Nebula requires Among Us "
                            + GameCompatibility.REQUIRED_VERSION + ".")
                    .setPositiveButton("Open Play Store", (dialog, which) -> openAmongUsStore())
                    .setNegativeButton("Cancel", null)
                    .show();
            return false;
        }

        if (GameCompatibility.isSupported(packageInfo)) {
            return true;
        }

        if (BuildConfig.DEBUG_MODE) {
            Log.w(TAG, "Debug compatibility override: allowing Among Us "
                    + GameCompatibility.getVersionName(packageInfo) + " ("
                    + GameCompatibility.getVersionCode(packageInfo) + ").");
            return true;
        }

        String installedVersion = GameCompatibility.getVersionName(packageInfo);
        long installedVersionCode = GameCompatibility.getVersionCode(packageInfo);
        String action = GameCompatibility.requiredAction(installedVersion);
        new NebulaDialogBuilder(this)
                .setTitle("Unsupported Among Us version")
                .setMessage("Nebula supports Among Us package version 2026.6.5 "
                        + "(Android build 7045) only. "
                        + "You currently have package version "
                        + (installedVersion.isEmpty() ? "unknown" : installedVersion)
                        + " (Android build " + installedVersionCode + ")"
                        + ". Please " + action + " Among Us before launching.")
                .setPositiveButton("Open Play Store", (dialog, which) -> openAmongUsStore())
                .setNegativeButton("Cancel", null)
                .show();
        return false;
    }

    private void showBlockingLoading(String title, String message) {
        if (blockingLoadingDialog != null && blockingLoadingDialog.isShowing()) {
            blockingLoadingDialog.dismiss();
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        wrap.setPadding(pad, dp(18), pad, dp(10));

        TextView spinner = text("...", 28, 0xFF22D3EE, Typeface.BOLD);
        spinner.setGravity(Gravity.CENTER);
        wrap.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = text(message, 14, 0xFF334155, Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(10), 0, 0);
        wrap.addView(body);

        blockingLoadingDialog = new NebulaDialogBuilder(this)
                .setTitle(title)
                .setView(wrap)
                .setCancelable(false)
                .create();
        blockingLoadingDialog.show();

        spinner.animate()
                .rotationBy(360f)
                .setDuration(900)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> {
                    if (blockingLoadingDialog != null && blockingLoadingDialog.isShowing()) {
                        spinner.setRotation(0f);
                        spinner.animate().rotationBy(360f).setDuration(900).setInterpolator(new LinearInterpolator()).start();
                    }
                })
                .start();
    }

    private void hideBlockingLoading() {
        if (blockingLoadingDialog != null) {
            blockingLoadingDialog.dismiss();
            blockingLoadingDialog = null;
        }
    }

    private void showAccountLinkDialog() {
        AppEntry target = getPrimaryTarget();
        if (target == null) {
            Toast.makeText(this, getString(R.string.selector_mods_requires_game), Toast.LENGTH_LONG).show();
            return;
        }

        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setTextColor(0xFFF0F3FF);
        input.setHintTextColor(0xFF8790B9);
        input.setHint("Paste the shared accounts.innersloth.com link");
        input.setBackground(roundedColor(0xEE202A48, dp(12), 0x554F7CFF, dp(1)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        wrap.setPadding(pad, dp(8), pad, 0);
        TextView message = text("Open Innersloth, sign in with Google, use Share Link, then paste that link here.", 13, 0xFF334155, Typeface.NORMAL);
        wrap.addView(message);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = dp(12);
        wrap.addView(input, inputParams);

        new NebulaDialogBuilder(this)
                .setTitle("Nebula account link")
                .setView(wrap)
                .setPositiveButton("Save", (dialog, which) -> saveAccountLink(target.packageName, input.getText().toString()))
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

    private void saveAccountLink(String packageName, String rawLink) {
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
            auth.append("ProfilePath=").append(escapeProperty(Utilities.getPrivateTargetDirectory(this, packageName).getAbsolutePath())).append('\n');

            Utilities.writeTextFile(Utilities.getAuthConfigFile(this, packageName), auth.toString());
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
            byte[] decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP);
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

    private void beginImportMods() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_IMPORT_MODS);
    }

    private int importSelectedMods(Intent data) {
        try {
            FusionRuntimeManager.ensureModDirectories(this);
        } catch (Exception e) {
            Log.w(TAG, "Failed to prepare BepInEx before mod import", e);
        }

        File pluginsDir = Utilities.getModsDirectory(this);
        if (!pluginsDir.exists() && !pluginsDir.mkdirs()) {
            Log.e(TAG, "Failed to create plugins directory: " + pluginsDir.getAbsolutePath());
            return 0;
        }

        List<Uri> uris = new ArrayList<>();
        Uri singleData = data.getData();
        if (singleData != null) {
            uris.add(singleData);
        }

        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }

        int importedCount = 0;
        for (Uri uri : uris) {
            String fileName = resolveDisplayName(uri);
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "imported-mod-" + System.currentTimeMillis() + ".bin";
            }

            String safeName = sanitizeFileName(fileName);
            if (safeName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) {
                        continue;
                    }
                    if (extractImportedZip(inputStream, Utilities.getNebulaRoot(this), pluginsDir, safeName)) {
                        importedCount++;
                        FusionRuntimeManager.modsChanged(this);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract imported zip: " + safeName, e);
                }
                continue;
            }

            File targetFile = new File(getSharedModImportDirectory(), safeName);
            File tempFile = new File(targetFile.getParentFile(),
                    "." + targetFile.getName() + "." + UUID.randomUUID() + ".importing");
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                if (inputStream == null) {
                    continue;
                }
                Utilities.copyStream(inputStream, outputStream, MAX_MOD_ZIP_ENTRY_BYTES);
                outputStream.getFD().sync();
                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                importedCount++;
                FusionRuntimeManager.modsChanged(this);
            } catch (Exception e) {
                if (tempFile.exists() && !tempFile.delete()) {
                    Log.w(TAG, "Failed to remove partial mod import: " + tempFile);
                }
                Log.e(TAG, "Failed to import mod file from uri: " + uri, e);
            }
        }

        return importedCount;
    }

    private File getSharedModImportDirectory() {
        File dir = Utilities.getModsDirectory(this);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create shared mods directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private boolean extractImportedZip(InputStream inputStream, File launcherRoot, File pluginsDir, String safeName) {
        String baseName = safeName;
        int dot = safeName.lastIndexOf('.');
        if (dot > 0) {
            baseName = safeName.substring(0, dot);
        }

        File stagingRoot = new File(getCacheDir(), "mod-import-" + System.nanoTime());
        File stagingPlugins = new File(stagingRoot, "plugins");
        File importRoot = new File(stagingPlugins, sanitizeFileName(baseName));
        try {
            if (!stagingPlugins.mkdirs()) {
                throw new IOException("Failed to create mod import staging directory");
            }
            stagingPlugins.getCanonicalPath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve zip import root", e);
            return false;
        }

        boolean extractedAny = false;
        int extractedEntries = 0;
        long extractedBytes = 0L;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (++extractedEntries > MAX_MOD_ZIP_ENTRIES) {
                    throw new IOException("Mod archive contains too many entries");
                }
                String entryName = entry.getName();
                if (entryName == null || entryName.isEmpty()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                ZipImportTarget importTarget = resolveZipImportTarget(
                        entryName,
                        stagingPlugins,
                        importRoot
                );
                if (importTarget == null) {
                    zipInputStream.closeEntry();
                    continue;
                }

                File targetFile = importTarget.file;
                String canonicalTarget = targetFile.getCanonicalPath();
                if (!canonicalTarget.startsWith(importTarget.allowRoot)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!targetFile.exists() && !targetFile.mkdirs()) {
                        Log.w(TAG, "Failed to create zip directory: " + targetFile.getAbsolutePath());
                    }
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        Log.w(TAG, "Failed to create parent directory: " + parent.getAbsolutePath());
                        zipInputStream.closeEntry();
                        continue;
                    }

                    try (FileOutputStream outputStream = new FileOutputStream(targetFile, false)) {
                        long entryBytes = 0L;
                        int count;
                        while ((count = zipInputStream.read(buffer)) != -1) {
                            entryBytes += count;
                            extractedBytes += count;
                            if (entryBytes > MAX_MOD_ZIP_ENTRY_BYTES
                                    || extractedBytes > MAX_MOD_ZIP_TOTAL_BYTES) {
                                throw new IOException("Mod archive exceeds the extraction size limit");
                            }
                            outputStream.write(buffer, 0, count);
                        }
                    }
                    extractedAny = true;
                }

                zipInputStream.closeEntry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed while extracting imported zip: " + safeName, e);
            Utilities.deleteRecursive(stagingRoot);
            return false;
        }
        if (!extractedAny) {
            Utilities.deleteRecursive(stagingRoot);
            return false;
        }
        try {
            Utilities.moveDirectoryContents(stagingPlugins, pluginsDir);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to commit staged mod import: " + safeName, e);
            return false;
        } finally {
            Utilities.deleteRecursive(stagingRoot);
        }
    }

    private ZipImportTarget resolveZipImportTarget(
            String entryName,
            File pluginsDir,
            File defaultImportRoot
    ) {
        String normalizedName = entryName.replace('\\', '/');
        while (normalizedName.startsWith("/")) {
            normalizedName = normalizedName.substring(1);
        }

        String lowerName = normalizedName.toLowerCase(Locale.ROOT);
        try {
            if (isNebulaOwnedRuntimeZipEntry(lowerName)) {
                return null;
            }

            int pluginsIndex = lowerName.startsWith("plugins/")
                    ? 0
                    : lowerName.indexOf("/plugins/");
            if (pluginsIndex >= 0) {
                int payloadStart = pluginsIndex == 0
                        ? "plugins/".length()
                        : pluginsIndex + "/plugins/".length();
                String relative = normalizedName.substring(payloadStart);
                if (relative.isEmpty() || relative.endsWith("/") || isNebulaOwnedRuntimeZipEntry(relative.toLowerCase(Locale.ROOT))) {
                    return null;
                }
                return new ZipImportTarget(new File(pluginsDir, relative), pluginsDir.getCanonicalPath() + File.separator);
            }

            if (isPluginPayloadFile(lowerName)) {
                return new ZipImportTarget(new File(defaultImportRoot, new File(normalizedName).getName()), defaultImportRoot.getCanonicalPath() + File.separator);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve zip target for " + entryName, e);
        }

        return null;
    }


    private boolean isPluginPayloadFile(String lowerName) {
        return lowerName.endsWith(".dll")
                || lowerName.endsWith(".bundle")
                || lowerName.endsWith(".catalog")
                || lowerName.endsWith(".json");
    }

    private boolean isNebulaOwnedRuntimeZipEntry(String lowerName) {
        return lowerName.equals("winhttp.dll")
                || lowerName.equals("version.dll")
                || lowerName.equals("doorstop_config.ini")
                || lowerName.equals(".doorstop_version")
                || lowerName.startsWith("dotnet/")
                || lowerName.startsWith("bepinex/core/")
                || lowerName.startsWith("bepinex/config/")
                || lowerName.startsWith("bepinex/interop/")
                || lowerName.startsWith("bepinex/patchers/")
                || lowerName.startsWith("bepinex/unity-libs/");
    }
    private void refreshModsUi() {
        File pluginsDir = Utilities.getModsDirectory(this);
        boolean gameInstalled = isAmongUsInstalled();
        if (gameInstalled) {
            try {
                FusionRuntimeManager.ensureModDirectories(this);
            } catch (Exception e) {
                Log.w(TAG, "Failed to prepare BepInEx while refreshing mods UI", e);
            }
        }
        targetStatusView.setText(gameInstalled ? "Android Among Us ready" : "Install Android Among Us");
        launchStatusView.setText(gameInstalled ? "ready" : "install game");
        storageStatusView.setText("Private storage ready");
        modsPathView.setText(pluginsDir.getAbsolutePath());

        File[] files = pluginsDir.listFiles();
        currentModFiles.clear();
        if (files != null) {
            for (File file : files) {
                if (!isInternalManagedFile(file)) {
                    currentModFiles.add(file);
                }
            }
        }

        Collections.sort(currentModFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        modsAdapter.notifyDataSetChanged();

        if (currentModFiles.isEmpty()) {
            modsCountView.setText("0 mods");
            deleteAllModsButton.setEnabled(false);
        } else {
            modsCountView.setText(currentModFiles.size() + " mods");
            deleteAllModsButton.setEnabled(hasExternallyAddedMods());
        }

        promoteUnfinishedLaunchIfNeeded("com.innersloth.spacemafia");
        refreshLastErrorUi("com.innersloth.spacemafia");
    }

    private void promoteUnfinishedLaunchIfNeeded(String packageName) {
        File sentinelFile = Utilities.getLaunchSentinelFile(this, packageName);
        if (!sentinelFile.isFile()) {
            return;
        }

        boolean interruptedInteropGeneration = getSharedPreferences("nebula_launch", MODE_PRIVATE)
                .getBoolean("il2cpp_interop_generation_in_progress", false);
        File bepInExLog = Utilities.getRuntimeLogFile(this);
        if (bepInExLog.isFile()) {
            try {
                String logTail = Utilities.readTailTextFile(bepInExLog, 65536);
                interruptedInteropGeneration = interruptedInteropGeneration ||
                        (logTail.contains("Running Cpp2IL")
                                || logTail.contains("Generating interop assemblies"))
                                && !logTail.contains("Chainloader startup complete");
            } catch (Exception e) {
                Log.w(TAG, "Failed to inspect BepInEx log after interrupted launch", e);
            }
        }

        StringBuilder report = new StringBuilder();
        if (interruptedInteropGeneration) {
            report.append("IL2CPP generation was interrupted.\n\n");
            report.append("Android closed Among Us while FusionCore was generating its first-run files. "
                    + "Launch again and wait; FusionCore will continue or regenerate what it needs.");
        } else {
            report.append("An error occurred.\n\n");
            report.append("The game closed before Nebula finished startup. "
                    + "This usually means FusionCore, BepInEx, or a mod crashed.");
        }

        File logFile = new File(Utilities.getRuntimeRoot(this), "logs/nebula-runtime.log");
        if (logFile.isFile()) {
            try {
                String tail = Utilities.readTailTextFile(logFile, 4096).trim();
                if (!tail.isEmpty()) {
                    report.append("\n\nRecent runtime log:\n").append(tail);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read runtime log tail after crash", e);
            }
        }

        try {
            Utilities.writeTextFile(Utilities.getLastErrorFile(this, packageName), report.toString());
        } catch (Exception e) {
            Log.w(TAG, "Failed to write crash recovery error report", e);
        }

        if (!sentinelFile.delete()) {
            Log.w(TAG, "Failed to clear launch sentinel after crash recovery: " + sentinelFile.getAbsolutePath());
        }
        getSharedPreferences("nebula_launch", MODE_PRIVATE)
                .edit()
                .remove("il2cpp_interop_generation_in_progress")
                .apply();

        if (interruptedInteropGeneration && !isFinishing()) {
            new NebulaDialogBuilder(this)
                    .setTitle("IL2CPP generation was interrupted")
                    .setMessage("Among Us crashed while generating IL2CPP interop. "
                            + "Relaunch it and wait again; FusionCore will continue or regenerate "
                            + "the files it needs.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void refreshLastErrorUi(String packageName) {
        File errorFile = Utilities.getLastErrorFile(this, packageName);
        if (!errorFile.isFile()) {
            errorCardView.setVisibility(View.GONE);
            return;
        }

        try {
            String text = Utilities.readTextFile(errorFile, 4096).trim();
            if (text.isEmpty()) {
                errorCardView.setVisibility(View.GONE);
                return;
            }
            lastErrorFullText = text;
            lastErrorView.setText(getBriefError(text));
            lastErrorView.setVisibility(View.VISIBLE);
            seeMoreLastErrorButton.setVisibility(View.VISIBLE);
            clearLastErrorButton.setVisibility(View.VISIBLE);
            errorCardView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read last error report", e);
            errorCardView.setVisibility(View.GONE);
        }
    }

    private void showLastErrorDetails() {
        new NebulaDialogBuilder(this)
                .setTitle("Error details")
                .setMessage(lastErrorFullText == null || lastErrorFullText.trim().isEmpty()
                        ? "No error details are available."
                        : lastErrorFullText)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String getBriefError(String fullText) {
        String[] lines = fullText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.equals("An error occurred.")
                    || trimmed.endsWith(":")) {
                continue;
            }
            if (trimmed.length() > 150) {
                return trimmed.substring(0, 147) + "...";
            }
            return trimmed;
        }
        return "The game closed unexpectedly.";
    }
    private void clearLastError() {
        File errorFile = Utilities.getLastErrorFile(this, "com.innersloth.spacemafia");
        if (errorFile.exists() && !errorFile.delete()) {
            Toast.makeText(this, getString(R.string.selector_clear_last_error_failed), Toast.LENGTH_LONG).show();
            return;
        }

        refreshLastErrorUi("com.innersloth.spacemafia");
    }

    private void confirmDeleteMod(File file) {
        if (isProtectedLauncherComponent(file)) return;
        new NebulaDialogBuilder(this)
                .setTitle(R.string.selector_mod_delete_confirm_title)
                .setMessage(getString(R.string.selector_mod_delete_confirm_message, file.getName()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteMod(file))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteMod(File file) {
        if (isProtectedLauncherComponent(file)) return;
        if (Utilities.deleteRecursive(file)) {
            FusionRuntimeManager.modsChanged(this);
            Toast.makeText(this, getString(R.string.selector_mod_deleted, file.getName()), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.selector_mod_delete_failed, file.getName()), Toast.LENGTH_LONG).show();
        }
        refreshModsUi();
    }

    private void confirmDeleteAllMods() {
        if (!hasExternallyAddedMods()) {
            return;
        }

        new NebulaDialogBuilder(this)
                .setTitle(R.string.selector_mod_delete_all_confirm_title)
                .setMessage(R.string.selector_mod_delete_all_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteAllMods())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteAllMods() {
        int deleted = 0;
        for (File file : new ArrayList<>(currentModFiles)) {
            if (isProtectedLauncherComponent(file)
                    || NpkgInstaller.isManagedSharedFile(this, file)) continue;
            if (Utilities.deleteRecursive(file)) {
                deleted++;
            }
        }

        if (deleted > 0) {
            FusionRuntimeManager.modsChanged(this);
        }
        refreshModsUi();
        if (deleted > 0) {
            Toast.makeText(this, getString(R.string.selector_mod_delete_all_success, deleted), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.selector_mod_delete_all_failed), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasExternallyAddedMods() {
        for (File file : currentModFiles) {
            if (!isProtectedLauncherComponent(file)
                    && !NpkgInstaller.isManagedSharedFile(this, file)) return true;
        }
        return false;
    }

    private boolean isProtectedLauncherComponent(File file) {
        return file != null && "NebulaCompat.dll".equalsIgnoreCase(file.getName());
    }

    private void openFileBrowser() {
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra(FileBrowserActivity.EXTRA_ROOT_PATH, getFilesDir().getAbsolutePath());
        startActivity(intent);
    }

    private boolean isAmongUsInstalled() {
        try {
            getPackageManager().getPackageInfo("com.innersloth.spacemafia", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void openAmongUsStore() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.innersloth.spacemafia"));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.innersloth.spacemafia")));
        }
    }

    private AppEntry getPrimaryTarget() {
        if (installedTargets.isEmpty()) {
            return null;
        }
        return installedTargets.get(0);
    }

    private String resolveDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve display name for uri: " + uri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private boolean isInternalManagedFile(File file) {
        return false;
    }

    private static final class ZipImportTarget {
        private final File file;
        private final String allowRoot;

        private ZipImportTarget(File file, String allowRoot) {
            this.file = file;
            this.allowRoot = allowRoot;
        }
    }

    private static final class AppEntry {
        private final String packageName;
        private final String label;
        private final Drawable icon;
        private final String versionName;
        private final long versionCode;

        private AppEntry(String packageName, String label, Drawable icon, String versionName, long versionCode) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }

        @NonNull
        @Override
        public String toString() {
            if (label.equals(packageName)) {
                return packageName;
            }
            return label + " (" + packageName + ")";
        }
    }

}


