package org.quanqi.androidnetworkdemo;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * By cindy on 7/24/15 10:08 AM.
 */
public class RequestManager {
    private static final String TAG = "RequestManager";

    private static RequestManager instance;

    private Map<String, SSLSocketFactory> socketFactoryMap;

    public static RequestManager getInstance(Context context) {
        if (instance == null) {
            instance = new RequestManager(context);
        }
        return instance;
    }

    public RequestQueue mRequestQueue;
    private OkHttpClient okHttpClient;
    private BitmapLruCache mLruCache;
    private ImageLoader mImageLoader;
    private DiskBasedCache mDiskCache;

    private RequestManager(Context context) {
        int MEM_CACHE_SIZE = 1024 * 1024
                * ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() / 3;
        okHttpClient = new OkHttpClient();
        mLruCache = new BitmapLruCache(MEM_CACHE_SIZE);
        mRequestQueue = newRequestQueue(context.getApplicationContext());
        mImageLoader = new ImageLoader(mRequestQueue, mLruCache);
        mDiskCache = (DiskBasedCache) mRequestQueue.getCache();
    }

    private SSLSocketFactory createSSLSocketFactory(Context context, int res, String password)
            throws CertificateException,
            NoSuchAlgorithmException,
            IOException,
            KeyStoreException,
            KeyManagementException {
        InputStream inputStream = context.getResources().openRawResource(res);
        KeyStore keyStore = KeyStore.getInstance("BKS");
        keyStore.load(inputStream, password.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sslContext.getSocketFactory();
    }

    private RequestQueue newRequestQueue(Context context) {
        RequestQueue requestQueue;
        try {
            String[] hosts = {"kyfw.12306.cn"};
            int[] certRes = {R.raw.kyfw};
            String[] certPass = {"asdfqaz"};
            socketFactoryMap = new Hashtable<>(hosts.length);

            for (int i = 0; i < certRes.length; i++) {
                int res = certRes[i];
                String password = certPass[i];
                SSLSocketFactory sslSocketFactory = createSSLSocketFactory(context, res, password);
                socketFactoryMap.put(hosts[i], sslSocketFactory);
            }

            HurlStack stack = new SelfSignSslOkHttpStack(socketFactoryMap);

            requestQueue = Volley.newRequestQueue(context, stack);
            requestQueue.start();
        } catch (KeyStoreException
                | CertificateException
                | NoSuchAlgorithmException
                | KeyManagementException
                | IOException e) {
            throw new RuntimeException(e);
        }
        return requestQueue;
    }

    public void addRequest(Request request, Object tag) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Add request:" + request.toString());
        }
        if (tag != null) {
            request.setTag(tag);
        }
        mRequestQueue.add(request);
    }

    public void cancelAll(Object tag) {
        mRequestQueue.cancelAll(tag);
    }

    public File getCachedImageFile(String url) {
        return mDiskCache.getFileForKey(url);
    }

    public Bitmap getMemoryBitmap(String key) {
        return mLruCache.get(key);
    }

    public ImageLoader.ImageContainer loadImage(String requestUrl,
                                                ImageLoader.ImageListener imageListener) {
        return loadImage(requestUrl, imageListener, 0, 0);
    }

    public ImageLoader.ImageContainer loadImage(String requestUrl,
                                                ImageLoader.ImageListener imageListener,
                                                int maxWidth,
                                                int maxHeight) {

        return mImageLoader.get(requestUrl, imageListener, maxWidth, maxHeight);
    }


}
