package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ImageView;
import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.BitmapCache.CacheKey;
import shibafu.yukari.media2.Media;
import shibafu.yukari.util.BitmapUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ImageLoaderTask extends AsyncTask<ImageLoaderTask.Params, Void, Bitmap> {
    public static final int RESOLVE_MEDIA = 0;
    public static final int RESOLVE_THUMBNAIL = 1;

    /** 一般の画像用 */
    private static final Executor IMAGE_EXECUTOR = THREAD_POOL_EXECUTOR;
    /** プロフィールアイコン用 */
    private static final Executor PROFILE_ICON_EXECUTOR = Executors.newFixedThreadPool(4);

    private Context context;
    private WeakReference<ImageView> imageViewRef;
    private String tag;

    private ImageLoaderTask(Context context, ImageView imageView) {
        this.context = context;
        imageViewRef = new WeakReference<>(imageView);
        if (imageView != null) {
            tag = imageView.getTag().toString();
        }
    }

    @Override
    protected Bitmap doInBackground(Params... params) {
        final Params param = params[0];
        if (context == null) return null;
        try {
            Bitmap image = BitmapCache.getImageFromDisk(param.url, param.cacheKey, context);
            //無かったらWebから取得だ！
            if (image == null) {
                Media.ResolveInfo resolveInfo = null;
                InputStream inputStream;
                if (param.media == null) {
                    // Mediaが設定されていない場合は実体解決処理は行わずURLに直接接続する
                    inputStream = new URL(param.url).openStream();
                } else {
                    switch (param.resolveMode) {
                        case RESOLVE_MEDIA:
                            resolveInfo = param.media.resolveMedia();
                            break;
                        case RESOLVE_THUMBNAIL:
                            resolveInfo = param.media.resolveThumbnail();
                            break;
                    }
                    if (resolveInfo == null) {
                        throw new FileNotFoundException("Resolve failed : " + param.media.getBrowseUrl());
                    }
                    inputStream = resolveInfo.getStream();
                }

                File tempFile = File.createTempFile("image", ".tmp", context.getExternalCacheDir());
                try {
                    BufferedInputStream is = new BufferedInputStream(inputStream);
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    try {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = is.read(buffer, 0, buffer.length)) != -1) {
                            fos.write(buffer, 0, length);
                        }
                    } finally {
                        fos.close();
                        is.close();
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                    options.inSampleSize = Math.max(options.outWidth / 512, options.outHeight / 512);
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    image = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                } finally {
                    tempFile.delete();
                    if (resolveInfo != null) {
                        resolveInfo.dispose();
                    }
                }
                //キャッシュに保存
                BitmapCache.putImage(param.url, image, context, param.cacheKey, !param.mosaic, true);
            }
            if (image != null && param.mosaic) {
                image = BitmapUtil.createMosaic(image);
                BitmapCache.putImage(generateCacheUrlForMosaic(param.url), image, context, param.cacheKey, true, false);
            }
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ImageView getImageViewInstance() {
        if (imageViewRef != null) {
            ImageView iv = imageViewRef.get();
            if (iv != null && tag.equals(iv.getTag())) {
                return iv;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        ImageView imageView = getImageViewInstance();
        if (imageView != null) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
            else {
                imageView.setImageResource(R.drawable.ic_states_warning);
            }
        }
    }

    public void executeWrapper(Executor executor, Params params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            this.executeOnExecutor(executor, params);
        } catch (RejectedExecutionException e) {
            executeWrapper(executor, params);
        }
    }

    public static void loadProfileIcon(Context context, ImageView imageView, String uri) {
        loadBitmap(context, imageView, uri, RESOLVE_MEDIA, BitmapCache.PROFILE_ICON_CACHE, false);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        loadBitmap(context, imageView, uri, RESOLVE_MEDIA, BitmapCache.IMAGE_CACHE, false);
    }

    public static void loadBitmap(Context context, ImageView imageView, Media media, @ResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic) {
        if (media == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
            return;
        }

        imageView.setTag(media.getBrowseUrl());
        Bitmap cache;
        if (mosaic) {
            cache = BitmapCache.getImageFromMemory(generateCacheUrlForMosaic(media.getBrowseUrl()), cacheKey);
        } else {
            cache = BitmapCache.getImageFromMemory(media.getBrowseUrl(), cacheKey);
        }
        if (cache != null && !cache.isRecycled()) {
            imageView.setImageBitmap(cache);
        } else {
            imageView.setImageResource(R.drawable.yukatterload);
            new ImageLoaderTask(context, imageView).executeWrapper(
                    BitmapCache.IMAGE_CACHE.equals(cacheKey) ? IMAGE_EXECUTOR : PROFILE_ICON_EXECUTOR,
                    new Params(resolveMode, cacheKey, mosaic, media));
        }
    }

    // fast-path mode (for Profile Icon)
    public static void loadBitmap(Context context, ImageView imageView, String uri, @ResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic) {
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
            return;
        }

        imageView.setTag(uri);
        Bitmap cache;
        if (mosaic) {
            cache = BitmapCache.getImageFromMemory(generateCacheUrlForMosaic(uri), cacheKey);
        } else {
            cache = BitmapCache.getImageFromMemory(uri, cacheKey);
        }
        if (cache != null && !cache.isRecycled()) {
            imageView.setImageBitmap(cache);
        } else {
            imageView.setImageResource(R.drawable.yukatterload);
            new ImageLoaderTask(context, imageView).executeWrapper(
                    BitmapCache.IMAGE_CACHE.equals(cacheKey) ? IMAGE_EXECUTOR : PROFILE_ICON_EXECUTOR,
                    new Params(resolveMode, cacheKey, mosaic, uri));
        }
    }

    public static void loadDrawable(final Context context, String uri, @CacheKey String mode, final DrawableLoaderCallback callback) {
        Bitmap cache = BitmapCache.getImageFromMemory(uri, mode);
        if (cache != null && !cache.isRecycled()) {
            callback.onLoadDrawable(new BitmapDrawable(context.getResources(), cache));
        } else {
            new ImageLoaderTask(context, null) {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    callback.onLoadDrawable(new BitmapDrawable(context.getResources(), bitmap));
                }
            }.executeWrapper(
                    BitmapCache.IMAGE_CACHE.equals(mode) ? IMAGE_EXECUTOR : PROFILE_ICON_EXECUTOR,
                    new Params(RESOLVE_MEDIA, mode, false, uri));
        }
    }

    private static String generateCacheUrlForMosaic(String url) {
        return "[mosaic]" + url;
    }

    public interface DrawableLoaderCallback {
        void onLoadDrawable(Drawable drawable);
    }

    static class Params {
        @ResolveMode public final int resolveMode;
        @CacheKey public final String cacheKey;
        public final boolean mosaic;

        @Nullable public final Media media; // if null -> fast-path mode!
        public final String url;

        private Params(@ResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic, @NonNull Media media) {
            this.resolveMode = resolveMode;
            this.cacheKey = cacheKey;
            this.mosaic = mosaic;
            this.media = media;
            this.url = media.getBrowseUrl();
        }

        private Params(@ResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic, @NonNull String url) {
            this.resolveMode = resolveMode;
            this.cacheKey = cacheKey;
            this.mosaic = mosaic;
            this.url = url;
            this.media = null;
        }
    }

    @IntDef({RESOLVE_MEDIA, RESOLVE_THUMBNAIL})
    public @interface ResolveMode {}
}
