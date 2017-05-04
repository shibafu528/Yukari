package info.shibafu528.yukari.magickdecoder;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import magick.ImageInfo;
import magick.MagickException;
import magick.MagickImage;
import magick.util.MagickBitmap;
import shibafu.yukari.service.IBitmapDecoderService;

public class MagickDecoderService extends Service {
    private final IBitmapDecoderService binder = new IBitmapDecoderService.Stub() {
        @Override
        public Bitmap decodeFromFile(String fileName, int maxDimension) throws RemoteException {
            MagickImage image = null;
            try {
                image = new MagickImage(new ImageInfo(fileName));
                return MagickBitmap.ToReducedBitmap(image, maxDimension);
            } catch (MagickException e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.destroyImages();
                }
            }
            return null;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder.asBinder();
    }
}
