// IBitmapDecoderService.aidl
package shibafu.yukari.service;

interface IBitmapDecoderService {
    Bitmap decodeFromFile(String fileName, int maxDimension);
}
