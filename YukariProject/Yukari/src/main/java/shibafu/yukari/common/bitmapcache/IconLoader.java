package shibafu.yukari.common.bitmapcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import shibafu.yukari.R;

/**
 * TL上の画像ロードのためのクラス。
 * ImageLoaderTaskではAsyncTaskが立ち上がりまくってパフォーマンスが低下するので、
 * こちらを使いスレッド数を制限して処理する
 * Created by shibafu on 14/09/20.
 */
public class IconLoader {
    private static final int THREADS_NUM = 4;

    private static BlockingQueue<Request> queue;
    private static Handler handler;

    static {
        queue = new LinkedBlockingDeque<>();

        for (int i = 0; i < THREADS_NUM; ++i) {
            new Thread(new Worker()).start();
        }

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Request request = (Request) msg.obj;
                ImageView imageView = request.getImageView();
                if (imageView != null && request.getUri().equals(imageView.getTag())) {
                    if (request.getBitmap() == null) {
                        imageView.setImageResource(R.drawable.ic_states_warning);
                    } else {
                        imageView.setImageBitmap(request.getBitmap());
                    }
                }
            }
        };
    }

    public static void loadBitmap(Context context, ImageView imageView, String uri) {
        imageView.setTag(uri);
        if (uri == null) {
            imageView.setImageResource(R.drawable.ic_states_warning);
        }
        else {
            Bitmap cache = BitmapCache.getImageFromMemory(uri, BitmapCache.PROFILE_ICON_CACHE);
            if (cache != null && !cache.isRecycled()) {
                imageView.setImageBitmap(cache);
            } else {
                imageView.setImageResource(R.drawable.yukatterload);
                queue.offer(new Request(context, imageView, uri));
            }
        }
    }

    private static class Worker implements Runnable {

        @Override
        public void run() {
            while (true) {
                Request request;

                try {
                    request = queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }

                if (!request.isAvailable()) continue;

                request.setBitmap(ImageLoaderTask.loadBitmapInternal(
                        request.getContext(), request.getUri(), BitmapCache.PROFILE_ICON_CACHE));

                Message msg = new Message();
                msg.obj = request;
                handler.sendMessage(msg);
            }
        }
    }

    private static class Request {
        private WeakReference<Context> context;
        private WeakReference<ImageView> imageView;
        private String uri;
        private Bitmap bitmap;

        private Request(Context context, ImageView imageView, String uri) {
            this.context = new WeakReference<>(context);
            this.imageView = new WeakReference<>(imageView);
            this.uri = uri;
        }

        public boolean isAvailable() {
            return getContext() != null && getImageView() != null;
        }

        public Context getContext() {
            return context != null ? context.get() : null;
        }

        public ImageView getImageView() {
            return imageView != null ? imageView.get() : null;
        }

        public String getUri() {
            return uri;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

}
