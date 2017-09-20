package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.IntDef;
import android.widget.ImageView;
import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.BitmapCache.CacheKey;
import shibafu.yukari.media2.Media;
import shibafu.yukari.media2.MediaFactory;
import shibafu.yukari.util.BitmapUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
            Bitmap image = BitmapCache.getImageFromDisk(param.media.getBrowseUrl(), param.cacheKey, context);
            //無かったらWebから取得だ！
            if (image == null) {
                Media.ResolveInfo resolveInfo = null;
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

                File tempFile = File.createTempFile("image", ".tmp", context.getExternalCacheDir());
                try {
                    BufferedInputStream is = new BufferedInputStream(resolveInfo.getStream());
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
                    options.inPreferredConfig = param.cacheKey.equals(BitmapCache.PROFILE_ICON_CACHE) && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
                    image = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                } finally {
                    tempFile.delete();
                    resolveInfo.dispose();
                }
                //キャッシュに保存
                BitmapCache.putImage(param.media.getBrowseUrl(), image, context, param.cacheKey);
            }
            if (image != null && param.mosaic) {
                Bitmap mosaicBitmap = BitmapUtil.createMosaic(image);
                image.recycle();
                image = mosaicBitmap;
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

    public static void loadProfileIcon(Context context, ImageView imageView, String uri) {
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
            return;
        }

        loadBitmap(context, imageView, MediaFactory.newInstance(uri), RESOLVE_MEDIA, BitmapCache.PROFILE_ICON_CACHE, false);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
            return;
        }

        loadBitmap(context, imageView, MediaFactory.newInstance(uri), RESOLVE_MEDIA, BitmapCache.IMAGE_CACHE, false);
    }

    public static void loadBitmap(Context context, ImageView imageView, Media media, @ResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic) {
        if (media == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
            return;
        }

        imageView.setTag(media.getBrowseUrl());
        Bitmap cache = BitmapCache.getImageFromMemory(media.getBrowseUrl(), cacheKey);
        if (cache != null && !cache.isRecycled()) {
            imageView.setImageBitmap(cache);
        } else {
            imageView.setImageResource(R.drawable.yukatterload);
            new ImageLoaderTask(context, imageView).executeOnExecutor(
                    BitmapCache.IMAGE_CACHE.equals(cacheKey) ? IMAGE_EXECUTOR : PROFILE_ICON_EXECUTOR,
                    new Params(resolveMode, cacheKey, media, mosaic));
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
            }.executeOnExecutor(
                    BitmapCache.IMAGE_CACHE.equals(mode) ? IMAGE_EXECUTOR : PROFILE_ICON_EXECUTOR,
                    new Params(RESOLVE_MEDIA, mode, MediaFactory.newInstance(uri), false));
        }
    }

    public interface DrawableLoaderCallback {
        void onLoadDrawable(Drawable drawable);
    }

    static class Params {
        @ResolveMode public final int resolveMode;
        @CacheKey public final String cacheKey;
        public final Media media;
        public final boolean mosaic;

        public Params(@ResolveMode int resolveMode, @CacheKey String cacheKey, Media media, boolean mosaic) {
            this.resolveMode = resolveMode;
            this.cacheKey = cacheKey;
            this.media = media;
            this.mosaic = mosaic;
        }
    }

    @IntDef({RESOLVE_MEDIA, RESOLVE_THUMBNAIL})
    public @interface ResolveMode {}
}
