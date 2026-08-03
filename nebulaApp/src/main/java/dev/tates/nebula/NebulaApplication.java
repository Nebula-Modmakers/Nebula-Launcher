package dev.tates.nebula;
import android.app.Application;
import android.content.res.Configuration;
import android.app.Activity;
import android.os.Bundle;
public final class NebulaApplication extends Application {
    @Override public void onCreate(){super.onCreate();LanguageManager.apply(this);LanguageManager.load(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b){a.getWindow().getDecorView().post(() -> LanguageManager.install(a.getWindow().getDecorView()));}
            @Override public void onActivityResumed(Activity a){LanguageManager.install(a.getWindow().getDecorView());}
            @Override public void onActivityStarted(Activity a){} @Override public void onActivityPaused(Activity a){}
            @Override public void onActivityStopped(Activity a){} @Override public void onActivitySaveInstanceState(Activity a,Bundle b){} @Override public void onActivityDestroyed(Activity a){}
        });}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);LanguageManager.apply(this);}
}
