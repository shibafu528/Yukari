package shibafu.yukari.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;

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
        int rotate = getExifRotate(is);
        is.close();
        is = context.getContentResolver().openInputStream(source);
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
     * @param input 画像ファイルのストリーム
     * @return
     */
    public static int getExifRotate(InputStream input) {
        ExifInterface exif;
        try {
            exif = new ExifInterface(input);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

        int rotate = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (rotate) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public static Bitmap createMosaic(Bitmap src) {
        Bitmap minimized = Bitmap.createScaledBitmap(src, 16, 16, false);
        Bitmap resized = Bitmap.createScaledBitmap(minimized, src.getWidth(), src.getHeight(), false);
        minimized.recycle();

        return resized;
    }
}
