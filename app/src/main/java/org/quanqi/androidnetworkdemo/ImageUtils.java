package org.quanqi.androidnetworkdemo;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class ImageUtils {

    private static final String TAG = "ImageUtils";

    private static final int MAX_TEXTURE_SIZE = getOpengl2MaxTextureSize();

    // A private constructor to hide the the implicit public one
    private ImageUtils() {

    }

    public static int getOpengl2MaxTextureSize() {
        int[] maxTextureSize = new int[1];
        maxTextureSize[0] = 2048;
        android.opengl.GLES20.glGetIntegerv(android.opengl.GLES20.GL_MAX_TEXTURE_SIZE,
                maxTextureSize, 0);
        return maxTextureSize[0];
    }

    /**
     * Get the size in bytes of a bitmap.
     *
     * @param bitmap
     * @return size in bytes
     */
    @SuppressLint("NewApi")
    public static int getBitmapSize(Bitmap bitmap) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            return bitmap.getByteCount();
        }
        // Pre HC-MR1
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    /**
     * Decode and sample down a bitmap from resources to the requested width and
     * height.
     *
     * @param res       The resources object containing the image data
     * @param resId     The resource id of the image data
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return A bitmap sampled down from the original with the same aspect
     * ratio and dimensions that are equal to or greater than the
     * requested width and height(inMutable)
     */
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth,
                                                         int reqHeight) {
        return decodeSampledBitmapFromResource(res, resId, reqWidth, reqHeight, false);
    }

    /**
     * Decode and sample down a bitmap from resources to the requested width and
     * height.
     *
     * @param res       The resources object containing the image data
     * @param resId     The resource id of the image data
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param isMutable 可编辑
     * @return A bitmap sampled down from the original with the same aspect
     * ratio and dimensions that are equal to or greater than the
     * requested width and height
     */
    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth,
                                                         int reqHeight, boolean isMutable) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        if (isMutable && VERSION.SDK_INT >= 11) {
            options.inMutable = true;
        }
        Bitmap result = BitmapFactory.decodeResource(res, resId, options);
        if (isMutable) {
            result = createMutableBitmap(result);
        }
        return result;
    }

    public static Bitmap decodeSampledBitmapFromFile(String filePath, int sampledSize) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = sampledSize;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    /**
     * Decode and sample down a bitmap from a file to the requested width and
     * height.
     *
     * @param filePath  The full path of the file to decode
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return A bitmap sampled down from the original with the same aspect
     * ratio and dimensions that are equal to or greater than the
     * requested width and height(inmutable)
     */
    public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        return decodeSampledBitmapFromFile(filePath, reqWidth, reqHeight, false);
    }

    /**
     * Decode and sample down a bitmap from a file to the requested width and
     * height.
     *
     * @param filePath  The full path of the file to decode
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @param isMutable 可编辑
     * @return A bitmap sampled down from the original with the same aspect
     * ratio and dimensions that are equal to or greater than the
     * requested width and height
     */
    public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight,
                                                     boolean isMutable) {
        if (filePath == null) {
            return null;
        }
        if (reqHeight == 0) {
            reqHeight = MAX_TEXTURE_SIZE;
        }
        if (reqWidth == 0) {
            reqWidth = MAX_TEXTURE_SIZE;
        }

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(filePath, options);

        if (options.outWidth == -1 || options.outHeight == -1) {
            return null;
        }

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        if (isMutable && VERSION.SDK_INT >= 11) {
            options.inMutable = true;
        }

        Bitmap result = null;

        if (options.outWidth > MAX_TEXTURE_SIZE || options.outHeight > MAX_TEXTURE_SIZE
                || (options.outHeight >= options.outWidth * 3)) {
            // 长图
            try {
                result = regionDecode(filePath, reqWidth, reqHeight, options.outWidth,
                        options.outHeight);
            } catch (IOException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        } else {
            result = BitmapFactory.decodeFile(filePath, options);
        }

        if (isMutable) {
            result = createMutableBitmap(result);
        }

        return result;
    }

    private static Bitmap regionDecode(String path, int reqWidth, int reqHeight, int outWidth,
                                       int outHeight) throws IOException {
        BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(path, true);
        if (reqWidth > outWidth) {
            reqWidth = outWidth;
        }
        if (reqHeight > outHeight) {
            reqHeight = outHeight;
        }

        return regionDecoder.decodeRegion(new Rect(0, 0, reqWidth, reqHeight), null);
    }

    /**
     * Calculate an inSampleSize for use in a
     * {@link BitmapFactory.Options} object when decoding
     * bitmaps using the decode* methods from
     * {@link BitmapFactory}. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap
     * having a width and height equal to or larger than the requested width and
     * height. This implementation does not ensure a power of 2 is returned for
     * inSampleSize which can be faster when decoding but results in a larger
     * bitmap which isn't as useful for caching purposes.
     *
     * @param options   An options object with out* params already populated (run
     *                  through a decode* method with inJustDecodeBounds==true
     * @param reqWidth  The requested width of the resulting bitmap
     * @param reqHeight The requested height of the resulting bitmap
     * @return The value to be used for inSampleSize
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
                                            int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;

        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int widthSampleSize = 0;
            int heightSampleSize = 0;
            if (reqWidth < width) {
                widthSampleSize = Math.round((float) width / (float) reqWidth);
            }
            if (reqHeight < height) {
                heightSampleSize = Math.round((float) height / (float) reqHeight);
            }
            inSampleSize = Math.max(widthSampleSize, heightSampleSize);
        }
        return inSampleSize;
    }

    /**
     * 通过srcbitmap 创建一个可编辑的bitmap
     *
     * @param src
     * @return
     */
    public static Bitmap createMutableBitmap(Bitmap src) {
        Bitmap result = null;
        if (src == null) {
            return null;
        }
        result = src.copy(Config.ARGB_8888, true);

        return result;
    }

    /**
     * 将subBmp图像合并到oriBmp中
     *
     * @param oriBmp
     * @param subBmp
     * @param oriRect subBmp中取出的bitmap需要填充到oriRect中的区域
     * @param subRect 从subBmp中取出的区域
     * @return
     */
    public static Bitmap mergeBitmap(Bitmap oriBmp, Bitmap subBmp, final Rect oriRect,
                                     final Rect subRect) {
        if (subBmp == null) {
            return oriBmp;
        }

        if (oriBmp == null) {
            return null;
        }

        if (!oriBmp.isMutable()) {
            oriBmp = createMutableBitmap(oriBmp);
        }

        Canvas canvas = new Canvas(oriBmp);
        canvas.drawBitmap(subBmp, subRect, oriRect, null);
        return oriBmp;
    }

    /**
     * 将subBmp图像合并到oriBmp中
     *
     * @param oriBmp
     * @param subBmp
     * @return oriBmp
     */
    public static Bitmap mergeBitmap(Bitmap oriBmp, Bitmap subBmp) {
        if (subBmp == null) {
            return oriBmp;
        }

        if (oriBmp == null) {
            return null;
        }

        return mergeBitmap(oriBmp, subBmp, new Rect(0, 0, oriBmp.getWidth(), oriBmp.getHeight()),
                new Rect(0, 0, subBmp.getWidth(), subBmp.getHeight()));
    }

    private static final PorterDuffXfermode SRC_IN_MODE = new PorterDuffXfermode(
            PorterDuff.Mode.SRC_IN);

    private final static Paint SRC_IN_PAINT = new Paint();

    static {
        SRC_IN_PAINT.setXfermode(SRC_IN_MODE);
    }

    /**
     * 遮罩图片
     *
     * @param dstBmp
     * @param mask
     * @return 遮罩后的图片
     */
    public static Bitmap maskBitmap(final Bitmap dstBmp, final Bitmap mask) {
        if (dstBmp == null || mask == null) {
            return dstBmp;
        }
        Bitmap result = Bitmap
                .createBitmap(dstBmp.getWidth(), dstBmp.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        int sc = canvas.saveLayer(0, 0, canvas.getWidth(), canvas.getHeight(), null,
                Canvas.MATRIX_SAVE_FLAG | Canvas.CLIP_SAVE_FLAG | Canvas.HAS_ALPHA_LAYER_SAVE_FLAG
                        | Canvas.FULL_COLOR_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG
        );
        canvas.drawBitmap(mask, new Rect(0, 0, mask.getWidth(), mask.getHeight()), new Rect(0, 0,
                dstBmp.getWidth(), dstBmp.getHeight()), null);
        canvas.drawBitmap(dstBmp, 0, 0, SRC_IN_PAINT);

        canvas.restoreToCount(sc);
        return result;
    }

    public static Bitmap convertToAlphaMask(Bitmap b) {
        Bitmap a = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Config.ALPHA_8);
        Canvas c = new Canvas(a);
        c.drawBitmap(b, 0.0f, 0.0f, null);
        return a;
    }


    public static Bitmap getBmpFromUri(Uri uri, ContentResolver resolver) {
        try {
            return MediaStore.Images.Media.getBitmap(resolver, uri);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return null;
        }
    }

    public static boolean saveBitmap(Bitmap bitmap, String filepath, String fileType) {
        if (bitmap == null || TextUtils.isEmpty(filepath)
                || TextUtils.isEmpty(fileType))
            return false;

        File file = new File(filepath);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            if (fileType.equals("jpg") || fileType.equals("jpeg")) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }

            fos.close();

            return true;

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return false;
    }
}
