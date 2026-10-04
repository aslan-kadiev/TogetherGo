package at.fhj.togethergo.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.IOException;

/**
 * Image helpers: decode photos from local storage at a suitable size and fix the camera
 * rotation.
 */
public final class ImageUtils {

    private ImageUtils() {
    }

    /** Decodes an image scaled down to roughly the requested size, in full quality. */
    public static Bitmap decodeSampled(String path, int reqWidth, int reqHeight) {
        return decodeSampled(path, reqWidth, reqHeight, false);
    }

    /**
     * Decodes an image scaled down to roughly the requested size. With {@code lowMemory},
     * RGB_565 is used (half the memory of ARGB_8888), which suits thumbnails on weaker devices.
     */
    public static Bitmap decodeSampled(String path, int reqWidth, int reqHeight, boolean lowMemory) {
        if (path == null) {
            return null;
        }

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        if (lowMemory) {
            options.inPreferredConfig = Bitmap.Config.RGB_565;
        }

        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            return null; // never crash the gallery just because one photo is huge
        }
        if (bitmap == null) {
            return null;
        }
        return applyExifRotation(path, bitmap);
    }

    /**
     * Calculates the largest sample size that still keeps the image at least as large as
     * requested.
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Rotates the bitmap according to its EXIF orientation. Returns the original bitmap if
     * the EXIF data cannot be read.
     */
    private static Bitmap applyExifRotation(String path, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
                default:
                    return bitmap;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (IOException | OutOfMemoryError e) {
            return bitmap; // keep the un-rotated bitmap rather than failing
        }
    }
}
