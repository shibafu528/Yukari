package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import shibafu.yukari.common.bitmapcache.BitmapCache.CacheKey;
import shibafu.yukari.common.imageloader.ImageLoader;
import shibafu.yukari.common.imageloader.ImageLoaderResultCallback;
import shibafu.yukari.common.imageloader.ResolveMode;
import shibafu.yukari.media2.Media;

/**
 * Created by Shibafu on 13/10/28.
 */
public final class ImageLoaderTask {
    public static final int RESOLVE_MEDIA = 0;
    public static final int RESOLVE_THUMBNAIL = 1;

    public static void loadProfileIcon(Context context, ImageView imageView, String uri) {
        ImageLoader.INSTANCE.load(context, uri)
                .setCacheGroup(BitmapCache.PROFILE_ICON_CACHE)
                .toImageView(imageView);
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        ImageLoader.INSTANCE.load(context, uri)
                .setCacheGroup(BitmapCache.IMAGE_CACHE)
                .toImageView(imageView);
    }

    public static void loadBitmap(Context context, ImageView imageView, Media media, @LegacyResolveMode int resolveMode, @CacheKey String cacheKey, boolean mosaic) {
        ImageLoader.INSTANCE.load(context, media)
                .setResolveMode(resolveMode == RESOLVE_MEDIA ? ResolveMode.MEDIA : ResolveMode.THUMBNAIL)
                .setCacheGroup(cacheKey)
                .setMosaic(mosaic)
                .toImageView(imageView);
    }

    public static void loadDrawable(final Context context, String uri, @CacheKey String mode, final DrawableLoaderCallback callback) {
        ImageLoader.INSTANCE.load(context, uri)
                .setResolveMode(ResolveMode.MEDIA)
                .setCacheGroup(mode)
                .asDrawable(new ImageLoaderResultCallback<Drawable>() {
                    @Override
                    public void onSuccess(Drawable drawable) {
                        callback.onLoadDrawable(drawable);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable e) {}
                }, null, null);
    }

    public interface DrawableLoaderCallback {
        void onLoadDrawable(Drawable drawable);
    }

    @IntDef({RESOLVE_MEDIA, RESOLVE_THUMBNAIL})
    public @interface LegacyResolveMode {}
}
