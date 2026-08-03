package dev.tates.nebula;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

final class ModIconLoader {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, Bitmap> CACHE = new ConcurrentHashMap<>();

    private ModIconLoader() {}

    static void load(String url, ImageView target, View fallback) {
        target.setTag(url);
        target.setImageDrawable(null);
        target.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            target.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            return;
        }
        WORKER.execute(() -> {
            try {
                Bitmap bitmap = download(url);
                CACHE.put(url, bitmap);
                MAIN.post(() -> {
                    if (!target.isAttachedToWindow() || !url.equals(target.getTag())) return;
                    target.setImageBitmap(bitmap);
                    target.setVisibility(View.VISIBLE);
                    fallback.setVisibility(View.GONE);
                });
            } catch (Exception ignored) {
                // Keep the initials visible when offline or if an icon fails.
            }
        });
    }

    private static Bitmap download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(true);
        connection.setRequestProperty("User-Agent", "Nebula-Android/1");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || connection.getContentLength() > MAX_BYTES) {
                throw new IOException("Invalid icon response");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new IOException("Icon too large");
                    output.write(buffer, 0, count);
                }
            }
            byte[] encoded = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1
                    || bounds.outWidth > 2048 || bounds.outHeight > 2048
                    || (long) bounds.outWidth * bounds.outHeight > 4_194_304L) {
                throw new IOException("Invalid icon dimensions");
            }
            Bitmap result = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
            if (result == null) throw new IOException("Icon decode failed");
            return result;
        } finally {
            connection.disconnect();
        }
    }
}
