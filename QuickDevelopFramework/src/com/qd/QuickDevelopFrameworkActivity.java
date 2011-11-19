package com.qd;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.qd.image.ImageLoader;
import com.qd.image.ImageLoader.ImageViewEntry;

public class QuickDevelopFrameworkActivity extends Activity implements OnScrollListener{
    private static final String TAG = QuickDevelopFrameworkActivity.class.getSimpleName();
    private static final String URL_DOMAIN = "http://www.anfone.com/";
    
    private ListView mList;
    private AppAdapter adapter;
    private ArrayList<String> datas;
    
    private Handler mHander = new Handler(){
       public void handleMessage(Message msg){
           adapter.notifyDataSetChanged();
           ImageLoader.getInstance().startLoad();
       }
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        initLayout();
        fillValues();
    }

    private void initLayout() {
        mList = (ListView) findViewById(R.id.list);
        ImageLoader imageLoader = ImageLoader.getInstance();
        imageLoader.prepare(mList);
        imageLoader.setCallBack(new ImageLoader.CallBack(){
            public void loadFinish(ImageViewEntry entry, Drawable drawable){
                entry.getImageView().setImageDrawable(drawable);
            }
        });

    }
    
    private void fillValues() {
        adapter = new AppAdapter();
        mList.setAdapter(adapter);
        
        mList.setOnScrollListener(this);
        new Thread(){
            public void run(){
                datas = getDatasFromHttp();
                Log.e(TAG, "datas = " + datas.toString());
                mHander.sendEmptyMessage(0);
            }
        }.start();
    }
    
    private ArrayList<String> getDatasFromHttp(){
        ArrayList<String> list = new ArrayList<String>();
        String urlString = "http://118.144.74.31:8013/appStore/soft-new!need";
        try{
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(60 * 1000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                byte[] data = readStream(is);
                String json = new String(data);
                Log.e(TAG, "json = " + json);
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                   JSONObject obj = jsonArray.optJSONObject(i);
                   String imagePath = obj.optString("iconImg");
                   list.add(imagePath);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return list;
    }
    
    private byte[] readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            bout.write(buffer, 0, len);
        }
        bout.close();
        inputStream.close();
        return bout.toByteArray();
    }
    private class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return datas != null ? datas.size() : 0;
        }

        @Override
        public String getItem(int arg0) {
            return datas != null ? datas.get(arg0) : "";
        }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        @Override
        public View getView(int arg0, View arg1, ViewGroup arg2) {
            if(arg1 == null){
                arg1 = LayoutInflater.from(getBaseContext()).inflate(R.layout.list_item, null);
            }
            ImageView imageView = (ImageView)arg1.findViewById(R.id.app_image);
            TextView imageText = (TextView)arg1.findViewById(R.id.app_text);
            String name = getItem(arg0);
            imageText.setText("" + arg0);
            ImageViewEntry entry = ImageLoader.getInstance().new ImageViewEntry();
            entry.setPosition(arg0)
            .setImageView(imageView)
            .setUrl(URL_DOMAIN + name)
            .setFileNmae(name);
            ImageLoader.getInstance().addImage(entry);
            
            return arg1;
        }
        
    }
    
    private static class ViewHolder{
        private ImageView image;
        private TextView imageName;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }
}