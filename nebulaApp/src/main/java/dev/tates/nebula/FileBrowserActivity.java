package dev.tates.nebula;


import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;

public class FileBrowserActivity extends Activity {
    public static final String EXTRA_ROOT_PATH = "root_path";
    private static final String TAG = "Nebula";
    private static final int MAX_EDITABLE_BYTES = 256 * 1024;

    private File rootDirectory;
    private File currentDirectory;
    private final List<File> visibleEntries = new ArrayList<>();
    private ArrayAdapter<File> adapter;
    private TextView pathView;
    private Button upButton;
    private View screenRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        screenRoot = findViewById(R.id.file_browser_root);
        int basePadding = Math.round(getResources().getDisplayMetrics().density * 16f);
        Utilities.applyWindowInsets(screenRoot, basePadding);

        ImageButton backButton = findViewById(R.id.file_browser_back);
        pathView = findViewById(R.id.file_browser_path);
        upButton = findViewById(R.id.file_browser_up);
        Button refreshButton = findViewById(R.id.file_browser_refresh);
        ListView listView = findViewById(R.id.file_browser_list);

        String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
        if (rootPath == null || rootPath.trim().isEmpty()) {
            finish();
            return;
        }

        rootDirectory = new File(rootPath);
        currentDirectory = rootDirectory;

        adapter = new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, visibleEntries) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                LinearLayout row;
                ImageView icon;
                TextView name;
                TextView meta;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                    icon = (ImageView) row.getChildAt(0);
                    LinearLayout textWrap = (LinearLayout) row.getChildAt(1);
                    name = (TextView) textWrap.getChildAt(0);
                    meta = (TextView) textWrap.getChildAt(1);
                } else {
                    row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    row.setBackgroundResource(R.drawable.bg_nebula_row);

                    icon = new ImageView(getContext());
                    icon.setColorFilter(Color.WHITE);
                    icon.setPadding(dp(9), dp(9), dp(9), dp(9));
                    icon.setBackgroundResource(R.drawable.bg_nebula_icon);
                    row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

                    LinearLayout textWrap = new LinearLayout(getContext());
                    textWrap.setOrientation(LinearLayout.VERTICAL);
                    textWrap.setPadding(dp(12), 0, 0, 0);
                    name = new TextView(getContext());
                    name.setTextColor(0xFFF0F3FF);
                    name.setTextSize(15);
                    name.setTypeface(Typeface.DEFAULT_BOLD);
                    meta = new TextView(getContext());
                    meta.setTextColor(0xFF8790B9);
                    meta.setTextSize(12);
                    textWrap.addView(name);
                    textWrap.addView(meta);
                    row.addView(textWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                File file = getItem(position);
                if (file != null) {
                    icon.setImageResource(file.isDirectory() ? R.drawable.ic_nebula_folder : R.drawable.ic_nebula_puzzle);
                    name.setText(file.getName());
                    meta.setText(file.isDirectory() ? "Folder - tap to open" : readableSize(file.length()) + " - tap to edit if text");
                } else {
                    name.setText("");
                    meta.setText("");
                }
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openEntry(visibleEntries.get(position)));

        upButton.setOnClickListener(v -> navigateUp());
        backButton.setOnClickListener(v -> UiMotion.finish(this, screenRoot));
        refreshButton.setOnClickListener(v -> refreshListing());

        refreshListing();
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        UiMotion.finish(this, screenRoot);
    }

    private void refreshListing() {
        visibleEntries.clear();
        File[] files = currentDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isHiddenLegacyEntry(file)) {
                    continue;
                }
                visibleEntries.add(file);
            }
        }

        Collections.sort(visibleEntries, Comparator
                .comparing(File::isFile)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        adapter.notifyDataSetChanged();
        pathView.setText(getString(R.string.file_browser_path, currentDirectory.getAbsolutePath()));
        upButton.setEnabled(!currentDirectory.equals(rootDirectory));
    }

    private boolean isHiddenLegacyEntry(File file) {
        if (!currentDirectory.equals(rootDirectory) || !file.isDirectory()) {
            return false;
        }

        String name = file.getName();
        return "com.innersloth.spacemafia".equals(name) || "mods".equals(name);
    }

    private void openEntry(File entry) {
        if (entry.isDirectory()) {
            currentDirectory = entry;
            refreshListing();
            return;
        }

        if (!isEditableTextFile(entry)) {
            Toast.makeText(this, getString(R.string.file_browser_file_not_editable), Toast.LENGTH_LONG).show();
            return;
        }

        if (entry.length() > MAX_EDITABLE_BYTES) {
            Toast.makeText(this, getString(R.string.file_browser_file_too_large), Toast.LENGTH_LONG).show();
            return;
        }

        showEditor(entry);
    }

    private void showEditor(File file) {
        String content;
        try {
            content = Utilities.readTextFile(file, MAX_EDITABLE_BYTES);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read editable file", e);
            Toast.makeText(this, getString(R.string.file_browser_open_failed), Toast.LENGTH_LONG).show();
            return;
        }

        EditText editor = new EditText(this);
        editor.setText(content);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.LTGRAY);
        editor.setBackgroundResource(R.drawable.bg_nebula_row);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setMinLines(12);
        editor.setHorizontallyScrolling(false);

        new NebulaDialogBuilder(this)
                .setTitle(file.getName())
                .setView(editor)
                .setPositiveButton(R.string.file_browser_save, (dialog, which) -> saveFile(file, editor.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveFile(File file, String content) {
        try {
            Utilities.writeTextFile(file, content);
            Toast.makeText(this, getString(R.string.file_browser_saved, file.getName()), Toast.LENGTH_LONG).show();
            refreshListing();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save editable file", e);
            Toast.makeText(this, getString(R.string.file_browser_save_failed), Toast.LENGTH_LONG).show();
        }
    }

    private void navigateUp() {
        if (currentDirectory.equals(rootDirectory)) {
            return;
        }

        File parent = currentDirectory.getParentFile();
        if (parent == null) {
            return;
        }

        currentDirectory = parent;
        refreshListing();
    }

    private boolean isEditableTextFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".cfg")
                || name.endsWith(".config")
                || name.endsWith(".ini")
                || name.endsWith(".json")
                || name.endsWith(".log")
                || name.endsWith(".txt")
                || name.endsWith(".xml")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".csv")
                || name.equals("doorstop_config.ini")
                || name.equals("last_error.txt");
    }

    private String readableSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
