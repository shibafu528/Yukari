package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import shibafu.yukari.af2015.R;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.util.BitmapUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ImageLoaderTask extends ParallelAsyncTask<ImageLoaderTask.Params, Void, Bitmap> {
    private Context context;
    private WeakReference<ImageView> imageViewRef;
    private String tag;

    private ImageLoaderTask(Context context, ImageView imageView) {
        this.context = context;
        imageViewRef = new WeakReference<>(imageView);
        tag = imageView.getTag().toString();
    }

    static Bitmap loadBitmapInternal(Context context, String uri, String mode, boolean mosaic) {
        if (context == null) return null;
        try {
            Bitmap image = BitmapCache.getImageFromDisk(uri, BitmapCache.IMAGE_CACHE, context);
            //無かったらWebから取得だ！
            if (image == null) {
                File tempFile = File.createTempFile("image", ".tmp", context.getExternalCacheDir());
                try {
                    URL imageUrl = new URL(uri);
                    BufferedInputStream is = new BufferedInputStream(imageUrl.openStream());
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
                    options.inPreferredConfig = mode.equals(BitmapCache.PROFILE_ICON_CACHE)? Bitmap.Config.ARGB_4444 : Bitmap.Config.ARGB_8888;
                    image = BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                } finally {
                    tempFile.delete();
                }
                //キャッシュに保存
                BitmapCache.putImage(uri, image, context, mode);
            }
            if (image != null && mosaic) {
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

    @Override
    protected Bitmap doInBackground(Params... params) {
        return loadBitmapInternal(context, params[0].getUri(), params[0].getMode(), params[0].getMosaic());
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
        IconLoader.loadBitmap(context, imageView, uri);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        loadBitmap(context, imageView, uri, BitmapCache.IMAGE_CACHE, false);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri, String mode, boolean mosaic) {
        imageView.setTag(uri);
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
        }
        else {
            Bitmap cache = BitmapCache.getImageFromMemory(uri, mode);
            if (cache != null && !cache.isRecycled()) {
                imageView.setImageBitmap(cache);
            } else {
                imageView.setImageResource(R.drawable.yukatterload);
                new ImageLoaderTask(context, imageView).executeParallel(new Params(mode, uri, mosaic));
            }
        }
    }

    public static void loadDrawable(final Context context, String uri, String mode, final DrawableLoaderCallback callback) {
        Bitmap cache = BitmapCache.getImageFromMemory(uri, mode);
        if (cache != null && !cache.isRecycled()) {
            callback.onLoadDrawable(new BitmapDrawable(context.getResources(), cache));
        } else {
            new ImageLoaderTask(context, null) {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    callback.onLoadDrawable(new BitmapDrawable(context.getResources(), bitmap));
                }
            }.executeParallel(new Params(mode, uri, false));
        }
    }

    public static interface DrawableLoaderCallback {
        void onLoadDrawable(Drawable drawable);
    }

    static class Params {
        private String mode;
        private String uri;
        private boolean mosaic;

        private Params(String mode, String uri, boolean mosaic) {
            this.mode = mode;
            this.uri = uri;
            this.mosaic = mosaic;
        }

        public String getMode() {
            return mode;
        }

        public String getUri() {
            return uri;
        }

        public boolean getMosaic() {
            return mosaic;
        }
    }
}
