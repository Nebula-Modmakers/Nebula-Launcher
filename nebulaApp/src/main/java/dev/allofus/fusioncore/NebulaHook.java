package dev.allofus.fusioncore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class NebulaHook {
    static {
        if (android.os.Build.VERSION.SDK_INT >= 28
                && !org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")) {
            throw new ExceptionInInitializerError("Could not enable ART hook access");
        }
        System.loadLibrary("nebulahook");
        if (!nativeInitialize()) throw new ExceptionInInitializerError("LSPlant initialization failed");
    }

    private NebulaHook() {}

    public abstract static class Callback {
        public void beforeCall(CallFrame frame) throws Throwable {}
        public void afterCall(CallFrame frame) throws Throwable {}
    }

    public static final class CallFrame {
        public final Object thisObject;
        public final Object[] args;
        private final HookRecord record;
        private Object result;
        private Throwable throwable;
        private boolean skipOriginal;

        private CallFrame(HookRecord record, Object receiver, Object[] args) {
            this.record = record; this.thisObject = receiver; this.args = args;
        }

        public Object getResult() { return result; }
        public void setResult(Object value) { result = value; throwable = null; skipOriginal = true; }
        public boolean hasThrowable() { return throwable != null; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable value) { throwable = value; skipOriginal = true; }
        public Object invokeOriginalMethod(Object receiver, Object... values) throws Exception {
            try { return record.backup.invoke(receiver, values); }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw error;
            }
        }
    }

    public static final class HookRecord {
        final Member target;
        final Callback callback;
        final boolean isStatic;
        Method backup;

        HookRecord(Member target, Callback callback) {
            this.target = target; this.callback = callback;
            this.isStatic = target instanceof Method && Modifier.isStatic(target.getModifiers());
        }

        @SuppressWarnings("unused")
        public Object callback(Object[] rawArgs) throws Throwable {
            Object receiver = isStatic ? null : rawArgs[0];
            Object[] methodArgs = isStatic ? rawArgs : Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
            CallFrame frame = new CallFrame(this, receiver, methodArgs);
            callback.beforeCall(frame);
            if (!frame.skipOriginal) {
                try { frame.result = backup.invoke(receiver, methodArgs); }
                catch (InvocationTargetException error) { frame.throwable = error.getCause(); }
            }
            callback.afterCall(frame);
            if (frame.throwable != null) throw frame.throwable;
            return frame.result;
        }
    }

    public static void hook(Member target, Callback callback) {
        if (!(target instanceof Method) && !(target instanceof Constructor)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked");
        }
        HookRecord record = new HookRecord(target, callback);
        record.backup = nativeHook(target, record);
        if (record.backup == null) throw new IllegalStateException("Could not hook " + target);
    }

    private static native boolean nativeInitialize();
    private static native Method nativeHook(Member target, HookRecord record);
}
