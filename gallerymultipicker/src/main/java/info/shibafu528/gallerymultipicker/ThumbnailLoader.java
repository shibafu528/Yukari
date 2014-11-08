package info.shibafu528.gallerymultipicker;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.util.Pair;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

class ThumbnailLoader extends ParallelAsyncTask<Pair<ContentResolver, Long>, Void, Bitmap> {
    private static LruCache<Long, Bitmap> cache = new LruCache<Long, Bitmap>(16*1024*1024) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getRowBytes() * value.getHeight();
        }
    };
    private static BitmapFactory.Options options = new BitmapFactory.Options();

    private WeakReference<ImageView> imageView;
    private String tag;

    static {
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inPurgeable = true;
    }

    ThumbnailLoader(ImageView imageView) {
        this.imageView = new WeakReference<>(imageView);
        this.tag = (String) imageView.getTag();
    }

    @Override
    protected Bitmap doInBackground(Pair<ContentResolver, Long>... params) {
        Bitmap bitmap = cache.get(params[0].second);
        if (bitmap == null) {
            bitmap = MediaStore.Images.Thumbnails.getThumbnail(params[0].first, params[0].second, MediaStore.Images.Thumbnails.MINI_KIND, options);
            if (bitmap != null) {
                cache.put(params[0].second, bitmap);
            }
        }
        return bitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        ImageView imageView = this.imageView != null ? this.imageView.get() : null;
        if (imageView != null && tag.equals(imageView.getTag())) {
            imageView.setImageBitmap(bitmap);
        }
    }
}