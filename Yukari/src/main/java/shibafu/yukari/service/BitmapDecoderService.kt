package shibafu.yukari.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import java.io.FileInputStream
import java.io.IOException

class BitmapDecoderService : Service() {
    override fun onBind(intent: Intent?): IBinder = object : IBitmapDecoderService.Stub() {
        override fun decodeFromFile(fileName: String?, maxDimension: Int): Bitmap? {
            if (fileName.isNullOrEmpty() || maxDimension < 1) {
                return null
            }

            try {
                //画像サイズを確認
                var fis = FileInputStream(fileName)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(fis, null, options)
                fis.close()

                //サイズを決定
                val scaleW = options.outWidth / 2048
                val scaleH = options.outHeight / 2048
                options.inSampleSize = Math.max(scaleW, scaleH)
                options.inJustDecodeBounds = false

                //実際の読み込みを行う
                fis = FileInputStream(fileName)
                val bitmap = BitmapFactory.decodeStream(fis, null, options)
                fis.close()

                return bitmap
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
    }
}