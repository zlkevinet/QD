//project copyright
package com.qd.image;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.appoole.R;
import com.appoole.data.Constants;
import com.appoole.utils.IOFolderUtils;
import com.appoole.utils.Log;

/**
 * @author Jimmy.Z
 * @since 2011-11-21
 */
public class DrawableLoader {
    
    //同时存在请求网络的线程数
    private static final int MAX_CONCURRENCE = 5;
    //缓存最大限制5M
    private static final long MAX_CACHE = 5 * 1024 * 1024 * 8;
    //请求服务器的域名
    private static final String DOMAIN = Constants.URL_ONLINE_DOMAIN;
    //默认图片的地址
    private static final int DEFAULT = R.drawable.icon;
    //文件保存的更目录名
    private static final String ROOT = IOFolderUtils.getCacheImageFolder().getAbsolutePath() + "/";
    
    private static final String TAG = DrawableLoader.class.getSimpleName();
    
    private OnLoadFinishListener mOnLoadFinishListener;
    private int mThreadCount;
    private long mCacheSize;
    private Drawable mDefaultImage;
    private Object mOptWaitLock = new Object();
    private Context mContext;
    
    //内存当中的图片缓存
    private Map<String, SoftReference<Drawable>> mImageCache = new HashMap<String, SoftReference<Drawable>>();
    //等待队列
    private ArrayList<String> mWaitImages = new ArrayList<String>();
    
    private DrawableLoader(){}
    
    /**
     * 获得实例
     * @return
     */
    public DrawableLoader getInstance(){
        return ImageLoaderHoder.INSTANCE;
    }
    
    /**
     * 获取图片
     * @param url
     * @return drawable if cache in phone or sdcard, 
     *          otherwhile null if it need request network 
     */
    public synchronized Drawable obtainImage(Context context, final String url){
        mContext = context;
        Log.e(TAG, "传入的URL:" + url);
        //获得图片保存的地址
        final String path = getImageFilePath(url);
        Log.e(TAG, "获得图片保存的地址:" + path);
        //判断是否在内存
        Drawable phoneCache = obtainInPhoneCache(path);
        if(phoneCache != null){
            Log.e(TAG, "存在内存中！");
            notifyNext();
            return phoneCache;
        }else{
            //判断是否在SDCARD
            Drawable sdcardImage = obtainInSdcard(path);
            if(sdcardImage != null){
                Log.e(TAG, "存在SDCARD中！");
                notifyNext();
                return sdcardImage;
            }else{
                //判断当前线程数量
                boolean over = overMaxLimit();
                if(over){
                    Log.e(TAG, "超出可同时运行的线程数量！");
                    //添加到等待队列中
                    addToWaitArray(url);
                }else{
                    //请求网络下载
                    new Thread("LoadImage" + (++mThreadCount)){
                        public void run(){
                            //请求联网过程
                            Log.e(TAG, "开启线程" + mThreadCount + "!正在下载中..");
                            
                            Drawable netDrawable = connectServer(url, path);
                            
                            //判断是否最大限度的内存
                            if(!isMaxMemoryLimit()){
                                Log.e(TAG, "保存到内存");
                                //保存到内存
                                saveInPhone(path, netDrawable);
                            }
                            notifyNext();
                        }
                    }.start();
                }
                //返回默认图片
                Log.e(TAG, "先使用默认图片");
                Drawable defaultImage = obtainDefault(context);
                return defaultImage;
            }
        }
    }
    
    /**
     * 联网获得图片
     * @param url 请求URL
     * @param path 保存路径
     * @return
     */
    private Drawable connectServer(String url, String path) {
        Drawable drawable = null;
        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(url);
            HttpResponse response = client.execute(get);
            HttpEntity entity = response.getEntity();
            long length = entity.getContentLength();
            InputStream is = entity.getContent();
            if (is != null) {
                //增加缓存大小
                mCacheSize += length;
                drawable = Drawable.createFromStream(is, path);
                //保存到SDCARD
                saveInSdcard(path, is);
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return drawable;
    }

    /**
     * 是否是最大的内存限制
     * @return
     */
    private boolean isMaxMemoryLimit() {
        if(mCacheSize >= MAX_CACHE){
            return true;
        }
        return false;
    }

    /**
     * 添加到等待队列
     * @param url
     */
    private void addToWaitArray(String url) {
        //是否已经存在
        synchronized(mOptWaitLock){
            if(!mWaitImages.contains(url)){
                mWaitImages.add(url);
            }
        }
    }

    /**
     * 获得默认图片
     * @return
     */
    private Drawable obtainDefault(Context context) {
        if(mDefaultImage == null){
            mDefaultImage = context.getResources().getDrawable(DEFAULT);
        }
        return mDefaultImage;
    }

    /**
     * 是否超出最多线程数
     * @return
     */
    private boolean overMaxLimit() {
        if(mThreadCount >= MAX_CONCURRENCE){
            return true;
        }
        return false;
    }

    /**
     * 获得sdcard的图片
     * @param path
     * @return
     */
    private Drawable obtainInSdcard(String path) {
        return Drawable.createFromPath(path);
    }

    /**
     * 获得在内存中的图片
     * @param path
     * @return
     */
    private Drawable obtainInPhoneCache(String path) {
        if(mImageCache.containsKey(path)){
            SoftReference<Drawable> softReference = mImageCache.get(path);
            if (softReference.get() != null) {
                return softReference.get();
            }
        }
        return null;
    }

    /**
     * 从图片的URL获得保存地址
     * @param url
     */
    private String getImageFilePath(String url) {
        String path = ROOT;
        path += url.replaceAll(DOMAIN, "");
        return path;
    }

    /**
     * 保存到SDCARD
     * @param path
     */
    private void saveInSdcard(String path, InputStream is){
        File file = new File(path);
        
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int ch = -1;
            int count = 0;
            while ((ch = is.read(buf)) != -1) {
                fileOutputStream.write(buf, 0, ch);
                count += ch;
            }
            fileOutputStream.flush();
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 保存到内存
     * @param path
     * @param drawable
     */
    private void saveInPhone(String path, Drawable drawable){
        //判断是否已经存在
        if(!mImageCache.containsKey(path)){
            SoftReference<Drawable> sofeDrawable = new SoftReference<Drawable>(drawable);
            mImageCache.put(path, sofeDrawable);
        }
    }
    
    /**
     * 设置完成监听
     * @param listener
     */
    public void setOnLoadFinishListener(OnLoadFinishListener listener){
        this.mOnLoadFinishListener = listener;
    }
    
    /**
     * 完成通知和开始下一次
     */
    private void notifyNext() {
        //获得下一个url
        obtainWait(mContext);
        //通知界面更新
        if(mOnLoadFinishListener != null){
            Log.e(TAG, "通知调用者完成一次下载");
            mOnLoadFinishListener.onLoadFinish();
        }
    }

    /**
     * 进行下一个操作
     * @param context
     */
    private void obtainWait(Context context) {
        synchronized(mOptWaitLock){
            if(mWaitImages.size() != 0){
                String url = mWaitImages.remove(0);
                Log.e(TAG, "=======开始下一个======");
                obtainImage(context, url);
            }
        }
    }


    private static class ImageLoaderHoder{
        private static DrawableLoader INSTANCE = new DrawableLoader();
    }
    
    public interface OnLoadFinishListener {
        void onLoadFinish();
    }
}
