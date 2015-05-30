package shibafu.dissonance.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Shibafu on 13/08/04.
 */
public class BitmapUtil {

    /**
     * 指定Uriの画像データを縮小します
     * @param context
     * @param source
     * @param outWidth 縮小後の幅
     * @param outHeight 縮小後の高さ
     * @param sourceSize 任意です。要素数2のint配列を渡せば返します
     * @return 縮小済みBitmap
     * @throws IOException
     */
    public static Bitmap resizeBitmap(Context context, Uri source, int outWidth, int outHeight, int[] sourceSize) throws IOException {
        InputStream is = context.getContentResolver().openInputStream(source);
        //Exifの回転情報を読み取る
        int rotate = getExifRotate(context, source);
        //情報のみのロードを行う
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, option);
        is.close();
        if (sourceSize != null && sourceSize.length >= 2) {
            sourceSize[0] = option.outWidth;
            sourceSize[1] = option.outHeight;
        }
        //スケール計算を行う
        int scaleW = option.outWidth / outWidth;
        int scaleH = option.outHeight / outHeight;
        option.inSampleSize = Math.max(scaleW, scaleH);
        option.inJustDecodeBounds = false;
        //再オープンして実際のデコード
        is = context.getContentResolver().openInputStream(source);
        Bitmap bmp = BitmapFactory.decodeStream(is, null, option);
        is.close();
        //サイズを整える
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float scale = Math.min((float)outWidth/w, (float)outHeight/h);
        Matrix matrix = new Matrix();
        if (rotate > 0) {
            matrix.postRotate(rotate, w / 2, h / 2);
        }
        matrix.postScale(scale, scale);
        bmp = Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);
        return bmp;
    }

    /**
     * Exif情報を読み取り、回転状態の値を抽出します
     * @param context Context
     * @param uri 画像ファイルUri
     * @return
     */
    public static int getExifRotate(Context context, Uri uri) {
        //Storage Access Framework経由のUriの場合は対処する
        if (uri.toString().startsWith("content://com.android.providers.media.documents/document/")) {
            Cursor c = context.getContentResolver().query(uri, null, null, null, null);
            c.moveToFirst();
            long id = Long.valueOf(c.getString(0).split(":")[1]);
            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            c.close();
        }

        String dataUri;
        if (uri.getScheme().contains("file")) {
            dataUri = uri.toString();
        }
        else {
            Cursor c = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            c.moveToFirst();
            dataUri = c.getString(0);
        }

        ExifInterface exif;
        try {
            exif = new ExifInterface(dataUri);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        int rotate = Integer.valueOf(exif.getAttribute(ExifInterface.TAG_ORIENTATION));
        switch (rotate) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return -1;
        }
    }

    public static Bitmap createMosaic(Bitmap src) {
        Bitmap minimized = Bitmap.createScaledBitmap(src, 16, 16, false);
        Bitmap resized = Bitmap.createScaledBitmap(minimized, src.getWidth(), src.getHeight(), false);
        minimized.recycle();

        return resized;
    }
}
