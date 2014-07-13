package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import shibafu.yukari.R;
import shibafu.yukari.common.async.ParallelAsyncTask;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ImageLoaderTask extends ParallelAsyncTask<ImageLoaderTask.Params, Void, Bitmap> {
    private Context context;
    private ImageView imageView;
    private String tag;

    private ImageLoaderTask(Context context, ImageView imageView) {
        this.context = context;
        this.imageView = imageView;
        tag = this.imageView.getTag().toString();
    }

    @Override
    protected Bitmap doInBackground(Params... params) {
        try {
            publishProgress();
            String uri = params[0].getUri();
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
                    BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                    options.inSampleSize = Math.max(options.outWidth / 320, options.outHeight / 320);
                    options.inJustDecodeBounds = false;
                    image = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                } finally {
                    tempFile.delete();
                }
                //キャッシュに保存
                BitmapCache.putImage(uri, image, context, params[0].getMode());
            }
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        if (imageView != null && tag.equals(imageView.getTag())) {
            imageView.setImageResource(R.drawable.yukatterload);
        }
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (imageView != null && tag.equals(imageView.getTag())) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
            else {
                imageView.setImageResource(R.drawable.ic_states_warning);
            }
        }
    }

    public static void loadProfileIcon(Context context, ImageView imageView, String uri) {
        loadBitmap(context, imageView, uri, BitmapCache.PROFILE_ICON_CACHE);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        loadBitmap(context, imageView, uri, BitmapCache.IMAGE_CACHE);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri, String mode) {
        imageView.setTag(uri);
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
        }
        else {
            Bitmap cache = BitmapCache.getImageFromMemory(uri, mode);
            if (cache != null && !cache.isRecycled()) {
                imageView.setImageBitmap(cache);
            } else {
                new ImageLoaderTask(context, imageView).executeParallel(new Params(mode, uri));
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
            }.executeParallel(new Params(mode, uri));
        }
    }

    public static interface DrawableLoaderCallback {
        void onLoadDrawable(Drawable drawable);
    }

    static class Params {
        private String mode;
        private String uri;

        private Params(String mode, String uri) {
            this.mode = mode;
            this.uri = uri;
        }

        public String getMode() {
            return mode;
        }

        public String getUri() {
            return uri;
        }
    }
}