package dev.tates.nebula;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.util.TypedValue;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageManager {
    private static final String PREFS = "nebula_display";
    private static final String KEY = "language";
    public static final String[] CODES = {"en", "es", "fr", "de", "pl", "nl", "pt", "ru", "hu", "sr"};
    public static final String[] NAMES = {"English", "Español", "Français", "Deutsch", "Polski", "Nederlands", "Português", "Русский", "Magyar", "Srpski"};
    private LanguageManager() {}
    public static String getCode(Context c) { return c.getSharedPreferences(PREFS, 0).getString(KEY, "en"); }
    public static int getIndex(Context c) { String code=getCode(c); for(int i=0;i<CODES.length;i++) if(CODES[i].equals(code)) return i; return 0; }
    public static void setLanguage(Context c, int index) { if(index<0||index>=CODES.length)return; c.getSharedPreferences(PREFS,0).edit().putString(KEY,CODES[index]).commit(); apply(c); }
    @SuppressWarnings("deprecation") public static void apply(Context c) { Locale locale=new Locale(getCode(c)); Locale.setDefault(locale); Configuration config=new Configuration(c.getResources().getConfiguration()); config.setLocale(locale); if(android.os.Build.VERSION.SDK_INT>=24) config.setLocales(new android.os.LocaleList(locale)); c.getResources().updateConfiguration(config,c.getResources().getDisplayMetrics()); }

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static final Map<String, List<String>> ORDERED_KEYS = new HashMap<>();
    private static boolean loaded;

    public static synchronized void load(Context context) {
        if (loaded) return;
        loaded = true;
        try (InputStream input = context.getAssets().open("translations.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            for (String code : CODES) {
                if ("en".equals(code) || !root.has(code)) continue;
                JSONObject values = root.getJSONObject(code);
                Map<String, String> map = new HashMap<>();
                List<String> keys = new ArrayList<>();
                java.util.Iterator<String> iterator = values.keys();
                while (iterator.hasNext()) { String key=iterator.next(); map.put(key, values.getString(key)); keys.add(key); }
                Collections.sort(keys, (left, right) -> Integer.compare(right.length(), left.length()));
                TRANSLATIONS.put(code, map); ORDERED_KEYS.put(code, keys);
            }
        } catch (Exception ignored) { }
    }

    public static CharSequence translate(Context context, CharSequence value) {
        if (value == null || value.length() == 0 || "en".equals(getCode(context))) return value;
        load(context); String source=value.toString(); Map<String,String> map=TRANSLATIONS.get(getCode(context));
        if (map == null) return value;
        String exact=map.get(source); if(exact!=null) return exact;
        for (String template : ORDERED_KEYS.get(getCode(context))) {
            if (!template.matches(".*%\\d+\\$[ds].*")) continue;
            Matcher tokenMatcher = Pattern.compile("%(\\d+)\\$[ds]").matcher(template);
            StringBuilder expression = new StringBuilder("^");
            int cursor = 0; List<Integer> argumentNumbers = new ArrayList<>();
            while (tokenMatcher.find()) {
                expression.append(Pattern.quote(template.substring(cursor, tokenMatcher.start()))).append("(.*?)");
                argumentNumbers.add(Integer.parseInt(tokenMatcher.group(1))); cursor=tokenMatcher.end();
            }
            expression.append(Pattern.quote(template.substring(cursor))).append("$");
            Matcher valueMatcher = Pattern.compile(expression.toString(), Pattern.DOTALL).matcher(source);
            if (!valueMatcher.matches()) continue;
            String result = map.get(template);
            for (int group=0; group<argumentNumbers.size(); group++) {
                int argument=argumentNumbers.get(group);
                result=result.replaceAll("%"+argument+"\\$[ds]", Matcher.quoteReplacement(valueMatcher.group(group+1)));
            }
            return result;
        }
        String translated=source;
        for(String key:ORDERED_KEYS.get(getCode(context))) {
            if(key.length()<5 || !translated.contains(key)) continue;
            translated=translated.replace(key,map.get(key));
        }
        return translated;
    }

    public static void skip(View view) { view.setTag(R.id.nebula_localization_skip, Boolean.TRUE); }

    public static void install(View root) {
        if (root == null || Boolean.TRUE.equals(root.getTag(R.id.nebula_localization_skip))) return;
        localizeTree(root);
        if (root.getTag(R.id.nebula_localization_source) != null) return;
        root.setTag(R.id.nebula_localization_source, "watching");
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> localizeTree(root));
    }

    public static void localizeTree(View view) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.nebula_localization_skip))) return;
        if (view instanceof TextView) {
            TextView textView=(TextView)view;
            if (textView instanceof Button && android.os.Build.VERSION.SDK_INT >= 26) {
                int maximum = Math.max(12, Math.round(textView.getTextSize()
                        / view.getResources().getDisplayMetrics().scaledDensity));
                textView.setSingleLine(false);
                textView.setMaxLines(3);
                textView.setAutoSizeTextTypeUniformWithConfiguration(
                        9, maximum, 1, TypedValue.COMPLEX_UNIT_SP);
            }
            if (textView instanceof EditText) {
                CharSequence hint=textView.getHint(); if(hint!=null) textView.setHint(translate(view.getContext(),hint));
            } else {
                CharSequence current=textView.getText(); Object previousOutput=view.getTag(R.id.nebula_localization_output);
                if(previousOutput==null || !current.toString().equals(previousOutput.toString())) {
                    CharSequence translated=translate(view.getContext(),current);
                    if(!translated.toString().equals(current.toString())) textView.setText(translated);
                    view.setTag(R.id.nebula_localization_output,translated.toString());
                }
            }
            CharSequence description=view.getContentDescription(); if(description!=null) view.setContentDescription(translate(view.getContext(),description));
        }
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) localizeTree(group.getChildAt(i)); }
    }
}
