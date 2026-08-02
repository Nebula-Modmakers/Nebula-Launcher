package dev.tates.nebula;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BugReportActivity extends Activity {
    public static final String EXTRA_ERROR = "error";
    private EditText summary, details; private Spinner category; private Button submit; private TextView status;
    @Override public void onCreate(Bundle state) { super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(18));root.setBackgroundResource(R.drawable.bg_nebula_screen);
        Button back=button("← Back");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(dp(100),dp(44)));
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.addView(label("Report a bug",28,true));form.addView(label("Review the diagnostics below, describe what happened, then choose Submit.",14,false));
        summary=input("What happened? (required)",1);details=input("More details",5);category=new Spinner(this);category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Launching","In game","Authentication","Mod installation","Downloading","Other"}));
        form.addView(summary);form.addView(details);form.addView(category);form.addView(label("Automatically attached",17,true));
        form.addView(label("• Recent Nebula error\n• Nebula runtime log\n• BepInEx LogOutput.log\n• App, Android, game, device and installed-mod versions\n\nTokens, email addresses, activation keys and passwords are redacted. Nothing is submitted until you press Submit.",13,false));
        submit=button("Submit report");submit.setOnClickListener(v->submit());form.addView(submit);status=label("",13,false);form.addView(status);scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        String error=getIntent().getStringExtra(EXTRA_ERROR);if(error!=null&&!error.isEmpty()){summary.setText(firstLine(error));details.setText(error);}
    }
    private void submit(){if(summary.getText().toString().trim().isEmpty()){status.setText("Describe what happened first.");return;}submit.setEnabled(false);status.setText("Preparing and uploading diagnostics…");new Thread(()->{try{
        SharedPreferences prefs=getSharedPreferences("nebula_account",MODE_PRIVATE);String token=SecureTokenStore.get(prefs,"serverSessionToken");if(token.isEmpty())throw new IOException("Sign in before submitting a report.");
        JSONObject body=new JSONObject().put("summary",summary.getText().toString()).put("details",details.getText().toString()).put("category",category.getSelectedItem().toString()).put("source",getIntent().hasExtra(EXTRA_ERROR)?"error-card":"settings");
        JSONObject env=new JSONObject().put("nebulaVersion",BuildConfig.VERSION_NAME).put("nebulaVersionCode",BuildConfig.VERSION_CODE).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL);
        try{android.content.pm.PackageInfo p=GameCompatibility.getPackageInfo(this,GameCompatibility.AMONG_US_PACKAGE);env.put("gameVersion",GameCompatibility.getVersionName(p)).put("gameVersionCode",GameCompatibility.getVersionCode(p));}catch(Exception ignored){}
        JSONArray mods=new JSONArray();File[] files=Utilities.getModsDirectory(this).listFiles();if(files!=null)for(File f:files)mods.put(f.getName());env.put("installedMods",mods);body.put("environment",env);
        JSONArray logs=new JSONArray();add(logs,"error",details.getText().toString());
        File runtimeLog=new File(Utilities.getRuntimeRoot(this),"logs/nebula-runtime.log");
        String nebulaLog=runtimeLog.isFile()?Utilities.readTailTextFile(runtimeLog,256*1024):captureNebulaLog();
        add(logs,"nebula",nebulaLog.isEmpty()?"Nebula diagnostic log was unavailable for this process.":nebulaLog);
        File bepInExLog=Utilities.getRuntimeLogFile(this);
        add(logs,"bepinex",bepInExLog.isFile()?Utilities.readTailTextFile(bepInExLog,256*1024):"BepInEx LogOutput.log does not exist yet. The game may not have been launched since Nebula app data was cleared.");
        body.put("logs",logs);
        HttpURLConnection c=(HttpURLConnection)new URL(ModCatalogClient.API_BASE+"/bugs").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}InputStream in=c.getResponseCode()<300?c.getInputStream():c.getErrorStream();String response=read(in);JSONObject result=new JSONObject(response);if(!result.optBoolean("success"))throw new IOException(result.optString("error","Submission failed"));runOnUiThread(()->{status.setText("Submitted. Reference: "+result.optString("reportId"));submit.setText("Submitted");});c.disconnect();
    }catch(Exception e){runOnUiThread(()->{status.setText(e.getMessage());submit.setEnabled(true);});}},"NebulaBugReport").start();}
    private void addFile(JSONArray a,String type,File f)throws Exception{if(f.isFile())add(a,type,Utilities.readTailTextFile(f,256*1024));}private void add(JSONArray a,String type,String content)throws Exception{if(content!=null&&!content.isEmpty())a.put(new JSONObject().put("type",type).put("content",content));}
    private String captureNebulaLog(){try{Process process=new ProcessBuilder("logcat","-d","--pid="+android.os.Process.myPid(),"-t","1200").redirectErrorStream(true).start();String text=read(process.getInputStream());process.waitFor();return text.length()>256*1024?text.substring(text.length()-256*1024):text;}catch(Exception ignored){return "";}}
    private String read(InputStream in)throws Exception{if(in==null)return "{}";ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(o.size()+n>1024*1024)throw new IOException("Response too large");o.write(b,0,n);}return o.toString("UTF-8");}
    private String firstLine(String s){int i=s.indexOf('\n');return(i<0?s:s.substring(0,i)).replace("An error occurred.","Game closed unexpectedly").trim();}
    private EditText input(String hint,int lines){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(0xFFF0F3FF);e.setHintTextColor(0xFF8790B9);e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setBackgroundResource(R.drawable.bg_nebula_button);e.setPadding(dp(14),dp(12),dp(14),dp(12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);e.setLayoutParams(p);return e;}
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(bold?0xFFF0F3FF:0xFFAAB4E8);t.setTypeface(null,bold?1:0);t.setPadding(0,dp(10),0,dp(6));return t;}private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(0xFFF0F3FF);b.setAllCaps(false);b.setBackgroundResource(R.drawable.bg_nebula_button);return b;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
