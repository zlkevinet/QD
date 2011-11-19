//project copyright
package com.qd.net;

import java.net.MalformedURLException;
import java.net.URL;

import android.webkit.URLUtil;

/**
 * Http 
 * @author Jimmy.Z
 * @since 2011-10-25
 */
public class HttpConnecter {
    //===============================//
    // static field
    //===============================//
    private static final int REQUEST_METHOD_POST = 0;
    private static final int REQUEST_METHOD_GET = 1;
    //===============================//
    // field
    //===============================//

    private HttpConnecter(){}
    //===============================//
    // override method
    //===============================//

    //===============================//
    // method
    //===============================//
    
    public void requestHttpRes(final String urlString) throws MalformedURLException{
        requestHttpRes(urlString, null);
    }
    
    public void requestHttpRes(final String urlString, final OnConnectedCallBack callback) throws MalformedURLException{
        //check valid string
        checkValidUrl(urlString);
        URL url = new URL(urlString);
        requestHttpRes(url, callback);
    }
    
    public void requestHttpRes(final URL url){
        requestHttpRes(url, null);
    }
    
    public void requestHttpRes(final URL url, final OnConnectedCallBack callback){
        requestHttpRes(url, REQUEST_METHOD_POST, callback);
    }
    
    public void requestHttpRes(final URL url, final int requestCode){
        requestHttpRes(url, requestCode, null);
    }
    
    public void requestHttpRes(final URL url, final int requestCode, final OnConnectedCallBack callback){
        requestHttpRes(url, requestCode, null, callback);
    }
    
    public void requestHttpRes(final URL url, final int requestCode, final RequestParameter requestParameter, final OnConnectedCallBack callback){
        if(requestCode == REQUEST_METHOD_GET){
            
        }else if(requestCode == REQUEST_METHOD_POST){
            
        }else{
            throw new IllegalArgumentException("The requestCode is invalid. Please check it and try again."); 
        }
    }
    
    private void checkValidUrl(String urlString) {
        if(!URLUtil.isHttpUrl(urlString)){
            throw new IllegalArgumentException(
                    "The url string you inputed is unvalid. Please check it and try it again. Your URL:"
                    + urlString);
        }
    }
    
    public static HttpConnecter getInstance(){
        return QdHttpClassHolder.INSTANCE;
    }
    //===============================//
    // inner class
    //===============================//
    
    /**
     * request parameters
     */
    public class RequestParameter{
        
    }
    
    /**
     * response values
     */
    public class ResponseValue{
        
    }
    
    private static class QdHttpClassHolder {
        private static final HttpConnecter INSTANCE = new HttpConnecter();
    }
    
    public interface OnConnectedCallBack {
        public void connected(URL srcURL, ResponseValue responseValue);
    }
}
