package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.RejectedExecutionException;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/10/28.
 */
public class ImageLoaderTask extends AsyncTask<String, Void, Bitmap> {
    private Context context;
    private ImageView imageView;
    private String tag;

    public ImageLoaderTask(Context context, ImageView imageView) {
        this.context = context;
        this.imageView = imageView;
        tag = this.imageView.getTag().toString();
    }

    @Override
    protected Bitmap doInBackground(String... params) {
        if (params[0] != null) {
            try {
                //キャッシュから画像を取得
                Bitmap image = ImageCache.getImage(params[0], context);
                if (image == null) {
                    publishProgress();
                    //無かったらWebから取得だ！
                    URL imageUrl = new URL(params[0]);
                    InputStream is = imageUrl.openStream();
                    image = BitmapFactory.decodeStream(is);
                    //キャッシュに保存
                    ImageCache.putImage(params[0], image, context);
                }
                return image;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        if (tag.equals(imageView.getTag())) {
            imageView.setImageResource(R.drawable.yukatterload);
        }
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (tag.equals(imageView.getTag())) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
            else {
                imageView.setImageResource(R.drawable.yukatterload);
            }
        }
    }

    public void executeIf(String... params) {
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
}
