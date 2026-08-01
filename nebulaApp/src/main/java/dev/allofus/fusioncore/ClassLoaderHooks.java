package dev.allofus.fusioncore;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.Objects;


public class ClassLoaderHooks {

    public static final String TAG = "ClassLoaderHooks";

    private static Method loadClassMethodViaReflection() {
        Method loadClassMethod = null;
        Class<?> clazz = Objects.requireNonNull(BootstrapActivity.class.getClassLoader()).getClass();

        while (loadClassMethod == null && clazz != null) {
            try {
                try {
                    Class.forName(clazz.getName(), true, BootstrapActivity.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    Log.wtf(TAG, "Class not found: " + clazz.getName(), e);
                }

                loadClassMethod = clazz.getDeclaredMethod("loadClass", String.class, boolean.class);
                loadClassMethod.setAccessible(true);
                Log.d(TAG, "Found loadClass method in class: " + clazz.getName());
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }

        return loadClassMethod;
    }

    // this is necessary for android to load classes from the game loader
    // without this, starting the resolved activity fails.
    public static void installHooks(ClassLoader gameClassLoader) {

        Method loadClassMethod = loadClassMethodViaReflection();
        ClassLoader myClassLoader = ClassLoaderHooks.class.getClassLoader();
        assert myClassLoader != null : "My classloader is null, this should never happen";

        NebulaHook.hook(loadClassMethod, new NebulaHook.Callback() {
            @Override
            public void afterCall(NebulaHook.CallFrame callFrame) {
                try {
                    // Only attempt fallback if class was not found
                    if (callFrame.getThrowable() instanceof ClassNotFoundException) {
                        String className = (String) callFrame.args[0];
                        boolean resolve = (boolean) callFrame.args[1];
                        Log.d(TAG, "afterLoadClass: class not found in default loader: " + className);

                        // Try our classloader
                        if (callFrame.thisObject == gameClassLoader) {
                            try {
                                Class<?> myClass = (Class<?>) callFrame.invokeOriginalMethod(myClassLoader, className, resolve);
                                callFrame.setResult(myClass);
                                Log.d(TAG, "Successfully loaded from our classloader: " + className);
                            } catch (Exception e) {
                                Log.d(TAG, "Class not found in our classloader: " + className);
                            }

                            return;
                        } else if (callFrame.thisObject == myClassLoader) {
                            // try loading from game classloader
                            try {
                                Class<?> gameClass = (Class<?>) callFrame.invokeOriginalMethod(gameClassLoader, className, resolve);
                                callFrame.setResult(gameClass);
                                Log.d(TAG, "Successfully loaded from game classloader: " + className);
                            } catch (Exception e) {
                                Log.d(TAG, "Class not found in game classloader: " + className);
                            }

                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in afterCall (CRITICAL)", e);
                }
            }
        });
    }
}
