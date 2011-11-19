//project copyright
package com.qd.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.URLUtil;
import android.widget.AbsListView;
import android.widget.ImageView;

/**
 * <p>
 * 针对AbsListView动态加载和请求远程图片
 * </p>
 * <pre>
 * public class Sample implements OnScrollListener{
 *      ListView sampleListView = ..;
 *      sampleListView.setOnScrollListener(this);
 *      sampleListView.setAdapter(..);
 *      ..
 *      ImageLoader imageLoader = ImageLoader.getInstance();
 *      imageLoader.prepare(sampleListView);
 *      imageLoader.setCallBack(new ImageLoader.CallBack(){
 *          public void loadFinish(ImageViewEntry entry, Drawable drawable){
 *              entry.getImageView().setImageDrawable(drawable);
 *          }
 *      });
 *      ..
 *      public class SampleAdapter extends BaseAdapter{
 *           public View getView(int position, View convertView, ViewGroup parent){
 *              ..
 *              ImageView imageView = ..;
 *              ..
 *              ImageViewEntry entry = new ImageViewEntry();
 *              entry.setImageView(imageView)
 *                   .setPosition(position)
 *                   .setUrl(url);
 *              imageLoader.addImage(entry);
 *              ..
 *           }
 *      }
 *      
 *      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
 *           int totalItemCount) {
 *           
 *      }
 *       
 *      public void onScrollStateChanged(AbsListView view, int scrollState) {
 *          if(scrollState == SCROLL_STATE_IDLE){
 *              imageLoader.startLoad();
 *          }else{
 *              imageLoader.stopLoad();
 *          }
 *      }
 *      ..
 *      public void onDestroy(){
 *          imageLoader.release();
 *      }
 * }
 * </pre>
 * @author Jimmy.Z
 * @since Nov 13, 2011
 */
public class ImageLoader {
	//===============================//
	// static field
	//===============================//

    private static final int MAX_IMAGE_CACHE_LEN = 100;
    
    private static final String TAG = ImageLoader.class.getSimpleName();
    
    private static final String SDCARD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.cache/download";
	//===============================//
	// field
	//===============================//
    
    
    //当前正在加载的ImageView
    private ImageViewEntry mCurrentLoadingImageView;
    
    private ImageLoaderThread mImageLoaderThread;
    
	private ArrayList<ImageViewEntry> mImageViewPool = new ArrayList<ImageViewEntry>();
	
	private Map<String, SoftReference<Drawable>> mImageCache = new HashMap<String, SoftReference<Drawable>>();
	
	//当前加载指针
	private int mCurrentPosition;
	private AbsListView mHostView;
	//显示图片的动画
	private Animation mShowImageAnimation;
	
	private CallBack mCallBack;
	
	private Object lock = new Object();
	
	private boolean isAnimating;
	private boolean keepOnPreload;
	
	private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            mCallBack.loadFinish(mCurrentLoadingImageView, (Drawable)msg.obj);
            Log.e(TAG, "开始显示动画");
            startAnimation();
        }
	    
	};
	
	private ImageLoader(){}
	
	/**
	 * 获得单例
	 * @return ImageLoader
	 */
	public static ImageLoader getInstance(){
		return InstanceHolder.INSTANCE;
	}
	
	//===============================//
	// method
	//===============================//

	/**
	 * 准备并初始化一段新的加载过程
	 * @param view 宿主 AbsListView
	 */
	public void prepare(AbsListView view){
        mHostView = view;
        synchronized (lock) {
            if(mCurrentLoadingImageView != null){
                stopLoad();
                mCurrentLoadingImageView = null;
            }
        }
        release();
	}

	/**
	 * 释放加载器
	 */
    public void release() {
        mShowImageAnimation = null;
        mCurrentPosition = 0;
        mImageViewPool.clear();
        stopLoad();
    }
	
	private synchronized void startAnimation(){
	    isAnimating = true;
	    if(mShowImageAnimation == null){
	        mShowImageAnimation = new AlphaAnimation(0,1);
	        mShowImageAnimation.setDuration(500);
	        mShowImageAnimation.setAnimationListener(new Animation.AnimationListener() {
	            
	            @Override
	            public void onAnimationStart(Animation animation) {
	            }
	            
	            @Override
	            public void onAnimationRepeat(Animation animation) {
	            }
	            
	            @Override
	            public void onAnimationEnd(Animation animation) {
	                Log.e(TAG, "动画结束");
	                isAnimating = false;
	                doNextLoad();
	            }
	        });
	    }else{
	        Log.e(TAG, "重新设置动画");
	        mShowImageAnimation.reset();
	    }
	    
	    mCurrentLoadingImageView.imageView.startAnimation(mShowImageAnimation);
	}
	
	/**
	 * 设置回调
	 */
	public void setCallBack(CallBack callBack){
	    mCallBack = callBack;
	}
	
	/**
	 * 将一个请求加入队列
	 * @param position
	 * @param imageViewEntry
	 */
	public synchronized void addImage(ImageViewEntry imageViewEntry){
	    //TODO 添加一个项中有多张图片请求
	    Log.e(TAG, "线程是否运行？" + mImageLoaderThread.hasNext);
	    if(!mImageLoaderThread.hasNext){
	       startLoad(); 
	    }
	    for (int i = 0; i < mImageViewPool.size(); i++) {
            ImageViewEntry entry = mImageViewPool.get(i);
            if(entry.isSame(imageViewEntry)){
                return;
            }
        }
        Log.e(TAG, "添加位置为" + imageViewEntry.position + "的图片");
        mImageViewPool.add(imageViewEntry);
	}
	
	/**
	 * 开始加载
	 */
	public void startLoad(){
	    Log.e(TAG, "开始加载");
	    orderList();
	    mImageLoaderThread = new ImageLoaderThread();
	    mImageLoaderThread.hasNext = true;
	    mImageLoaderThread.start();
	}
	
	private void continueLoad(){
	    Log.e(TAG, "继续加载");
	    mImageLoaderThread = new ImageLoaderThread();
        mImageLoaderThread.hasNext = true;
        mImageLoaderThread.start();
	}
	
	/**
	 * 停止继续加载
	 */
	public void stopLoad(){
	    Log.e(TAG, "停止加载");
	    isAnimating = false;
	    if(mImageLoaderThread != null){
	        mImageLoaderThread.hasNext = false;
	    }
	}
	
	/**
	 * 获得指定位置的图片元素
	 * @param position
	 * @return 
	 */
	private ImageViewEntry getImageViewEntry(int position){
	    if(mImageViewPool == null || mImageViewPool.size() == 0){
	        return null;
	    }
	    return mImageViewPool.get(position);
	}
	
	/**
	 * 重排加载顺序
	 */
	private void orderList(){
	    mCurrentPosition = 0;
	}
	
	/**
	 * 缓存图片到内存
	 * @param position
	 * @param drawable
	 */
	private void saveDrawableInCache(final String url, final Drawable drawable){
	    if(mImageCache.size() >= MAX_IMAGE_CACHE_LEN){
	        mImageCache.clear();
	    }
	    mImageCache.put(url, new SoftReference<Drawable>(drawable));
	}
	
	/**
	 * 保存图片到SDCard
	 * @param url
	 * @param drawable
	 */
	private void saveDrawableInSdCard(final InputStream is, final String filename){
	    if(is == null || filename == null || filename.equals("")){
	        return;
	    }
	    try{
	        
    	    FileOutputStream fileOutputStream = null;
            if (is != null) {
                File file = new File(SDCARD_PATH, filename);
                
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                
                fileOutputStream = new FileOutputStream(file);
                byte[] buf = new byte[1024];
                int ch = -1;
                int count = 0;
                while ((ch = is.read(buf)) != -1) {
                    fileOutputStream.write(buf, 0, ch);
                    count += ch;
                }
            }
            fileOutputStream.flush();
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
	    }catch (Exception e) {
	        Log.e(TAG, "保存图片出错！" + e.getMessage());
        }
	}
	
	/**
	 * SDCard图片
	 * @param url
	 * @return drawable
	 */
	private Drawable getDrawableSDCard(ImageViewEntry entry) {
	    String imagePath = SDCARD_PATH + entry.getFileName();
	    Log.e(TAG, "从SDCard获取图片的地址：" + imagePath);
	    return Drawable.createFromPath(imagePath);
    }
	
	/**
	 * 从缓存中获取图片
	 * @param position
	 * @return
	 */
	public Drawable getDrawableCache(final String url){
	    SoftReference<Drawable> softReference = mImageCache.get(url);
        if (softReference != null 
                && softReference.get() != null 
                && mCallBack != null) {
            return softReference.get();
        }
        return null;
	}
	
	/**
	 * 指针下移，请求下一个图片
	 * @return
	 */
	private boolean nextRequest() {
	    //指针下移
	    mCurrentPosition++;
	    if(mCurrentPosition >= mHostView.getChildCount()){
	        mCurrentPosition = 0;
	        return false;
	    }
	    return true;
	}
	
	/**
	 * 联网方式
	 * @param imageUrl
	 * @return
	 * @throws Exception
	 */
	public InputStream connect(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30 * 1000);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() == 200) {
            return conn.getInputStream();
        }
        return null;
    }
	
	private void doNextLoad() {
        if(!nextRequest()){
            //结束加载过程
            stopLoad();
        }
        mCurrentLoadingImageView = null;
        if(keepOnPreload){
            keepOnPreload = false;
            continueLoad();
        }
    }
	
    //===============================//
	// inner class
	//===============================//

    private class ImageLoaderThread extends Thread {

	    //是否继续下一个加载过程
	    private boolean hasNext;
	    
        @Override
        public void run() {
            synchronized (lock) {
                while(hasNext){
                    //动画中
                    
                    //当没有ImageViewPool的时候
                    if(mImageViewPool.size() == 0){
                        Log.e(TAG, "队列为空，没有可加载的图片");
                        stopLoad();
                        break;
                    }
                    //如果当前加载项不为空，等待加载完成
                    if(mCurrentLoadingImageView != null){
                        Log.e(TAG, "继续上一次未加载完成的操作");
                        keepOnPreload = true;
                        stopLoad();
                        break;
                    }
                    
                    if(mHostView == null){
                        Log.e(TAG, "没有设置宿主视图");
                        stopLoad();
                        break;
                    }
                    
                    if(isAnimating){
                        Log.e(TAG, "动画显示中");
                        continue;
                    }
                    
                    //按顺序加载图片
                    final int pos = mHostView.getFirstVisiblePosition() + mCurrentPosition;
                    Log.e(TAG, "pos = " + pos);
                    mCurrentLoadingImageView = getImageViewEntry(pos);
                    
                    //没有加载项，停止加载
                    if(mCurrentLoadingImageView == null){
                        Log.e(TAG, "未找到指定位置图片，没有可加载的图片! pos = " + pos);
                        stopLoad();
                        break;
                    }
                    
                    if(mCurrentLoadingImageView.isLoaded){
                        Log.e(TAG, "已经完成下载");
                        stopLoad();
                        mCurrentLoadingImageView = null;
                        break;
                    }
                    //如果存在缓存中
                    Log.e(TAG, "从缓存中获取");
                    Drawable drawable = getDrawableCache(mCurrentLoadingImageView.url);
                    if(drawable == null){
                        Log.e(TAG, "缓存中未找到，从SDCard获取");
                        //如果存在SDCARD中
                        drawable = getDrawableSDCard(mCurrentLoadingImageView);
                    }
                    if(drawable == null){
                        Log.e(TAG, "从网络获取图片");
                        //请求网络获取图片
                        drawable = mCurrentLoadingImageView.downloadDrawable();
                    }
                    Log.e(TAG, "drawable = " + drawable);
                    Log.e(TAG, "mCallBack = " + mCallBack);
                    if (drawable != null && mCallBack != null) {
                        
                        
                        saveDrawableInCache(mCurrentLoadingImageView.url, drawable);
                        Message msg = new Message();
                        msg.obj = drawable;
                        mHandler.sendMessage(msg);
                        
                        //标记为已下载
                        mCurrentLoadingImageView.isLoaded = true;
                        //标记为动画
                        isAnimating = true;
                        
                        continue;
                    }
                    
                    Log.e(TAG, "无法获取图片，使用默认图片");
                    doNextLoad();
                }// end while
            }//end lock
        }
	    
	}
	
	public class ImageViewEntry{
        //图片的标识
		private int position;
		//图片的载体
		private ImageView imageView;
		//是否已经完成加载
		private boolean isLoaded;
		//请求网络地址
		private String url;
		//文件保存到SDCard的名字
		private String fileName;
		
		public ImageViewEntry setPosition(int position) {
            this.position = position;
            return this;
        }

        public ImageViewEntry setImageView(ImageView imageView) {
            this.imageView = imageView;
            return this;
        }

        public ImageViewEntry setUrl(String url) {
            this.url = url;
            return this;
        }
        
        public ImageViewEntry setFileNmae(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public int getPosition() {
            return position;
        }

        public ImageView getImageView() {
            return imageView;
        }

        public String getUrl() {
            return url;
        }

        public String getFileName(){
            if(fileName == null){
                //设置默认的文件名
                //eg.http://example.com/a/b/c.png
                //-->目录/a/b/c.png
                //eg.http://example.com/a/b/c
                //-->目录/a/b/c.png
                if(URLUtil.isValidUrl(url)){
                    URL encodeUrl = null;
                    try {
                        encodeUrl = new URL(url);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    String path = encodeUrl.getPath();
                    Log.e(TAG, "url path is " + path);
                    if(path != null && !path.equals("")){
                        //判断后缀是否有.png
                        int dotPos = path.lastIndexOf(".");
                        if(dotPos != -1){
                            
                            String suffix = path.substring(dotPos);
                            if(!suffix.equals(".png")){
                                path += ".png";
                            }
                        }else{
                            path += ".png";
                        }
                    }
                    fileName = path;
                }
            }
            return fileName;
        }
        
        /**
		 * 判断对象是不是相等
		 * @param imageViewEntry
		 * @return true 为相等对象 false 为不相等对象
		 */
		public boolean isSame(ImageViewEntry imageViewEntry){
			if(imageViewEntry != null){
			    if(imageViewEntry.position == position){
			        return true;
			    }
			}
			return false;
		}

		/**
		 * 从网络下载图片
		 * @return
		 */
		public Drawable downloadDrawable(){
		    InputStream is = null;
            try {
                is = connect(url);
            } catch (Exception e) {
                e.printStackTrace();
            }
		    if(is != null){
		        if(fileName == null){
		            fileName = getFileName();
		        }
		        Drawable drawable = Drawable.createFromStream(is, fileName);
		        Log.e(TAG, "文件名字为：" + fileName);
		        saveDrawableInSdCard(is, fileName);
		        return drawable;
		    }
		    return null;
		}
	}
	
	private static final class InstanceHolder{
		public static ImageLoader INSTANCE = new ImageLoader();
	}
	
	public interface CallBack{
	    public void loadFinish(ImageViewEntry entry, Drawable drawable);
	}

}
