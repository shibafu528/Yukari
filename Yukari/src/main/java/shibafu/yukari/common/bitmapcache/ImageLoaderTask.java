package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.RejectedExecutionException;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ImageLoaderTask extends AsyncTask<ImageLoaderTask.Params, Void, Bitmap> {
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
            //無かったらWebから取得だ！
            URL imageUrl = new URL(params[0].getUri());
            InputStream is = imageUrl.openStream();
            Bitmap image = BitmapFactory.decodeStream(is);
            //キャッシュに保存
            BitmapCache.putImage(params[0].getUri(), image, context, params[0].getMode());
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

    public void executeIf(Params... params) {
        if (getStatus() == Status.RUNNING && !isCancelled()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                this.executeOnExecutor(THREAD_POOL_EXECUTOR, params);
            }
            else {
                this.execute(params);
            }
        } catch (RejectedExecutionException e) {
            executeIf(params);
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
            Bitmap cache = BitmapCache.getImage(uri, context, mode);
            if (cache != null) {
                imageView.setImageBitmap(cache);
            } else {
                new ImageLoaderTask(context, imageView).executeIf(new Params(mode, uri));
            }
        }
    }

    public static void loadDrawable(final Context context, String uri, String mode, final DrawableLoaderCallback callback) {
        Bitmap cache = BitmapCache.getImage(uri, context, mode);
        if (cache != null) {
            callback.onLoadDrawable(new BitmapDrawable(context.getResources(), cache));
        } else {
            new ImageLoaderTask(context, null) {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    callback.onLoadDrawable(new BitmapDrawable(context.getResources(), bitmap));
                }
            }.executeIf(new Params(mode, uri));
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
