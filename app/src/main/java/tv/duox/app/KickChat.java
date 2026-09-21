package tv.duox.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;

/** Official Kick popout, displayed read-only beside the native video player. */
final class KickChat implements AutoCloseable {
    final WebView view;
    private boolean closed;
    /** Keeps only the message list of Kick's popout: no header, no input box, no Kick branding, transparent like the Twitch chat. */
    private static final String CLEAN="(function(){if(document.getElementById('duox-clean'))return;var s=document.createElement('style');s.id='duox-clean';s.textContent="+
        "'html,body,[class*=\"bg-surface-bg-lowest\"]{background:transparent!important}"+
        "[class*=\"border-b-2\"][class*=\"justify-between\"]{display:none!important}"+
        "[class*=\"z-common\"][class*=\"py-3\"]{display:none!important}"+
        ".contain-strict svg{display:none!important}"+
        "[class*=\"overflow-hidden\"][class*=\"grow\"]>[class*=\"absolute\"][class*=\"empty:hidden\"]:first-child{display:none!important}"+
        "';document.head.appendChild(s)})()";
    private final String path;
    @SuppressLint("SetJavaScriptEnabled") KickChat(Context context,String slug){
        path="/popout/"+slug+"/chat";
        view=new WebView(context);view.setBackgroundColor(Color.TRANSPARENT);
        view.setFocusable(false);view.setFocusableInTouchMode(false);
        // The overlay is for viewing; remote navigation stays with DuoX's controls.
        view.setOnTouchListener((v,event)->true);
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setTextZoom(70);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){Uri u=r.getUrl();return !("https".equals(u.getScheme())&&"kick.com".equals(u.getHost())&&path.equals(u.getPath()));}
            @Override public void onPageFinished(WebView w,String u){if(closed)return;w.evaluateJavascript(CLEAN,null);w.postDelayed(()->{if(!closed)w.evaluateJavascript(CLEAN,null);},2500);}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()&&!closed)showError();}
            @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame()&&!closed)showError();}
        });
        view.loadUrl("https://kick.com"+path);
    }
    private void showError(){view.loadDataWithBaseURL(null,"<html><body style='background:#0c1519;color:#9fb5af;font:18px sans-serif;padding:24px'>No se pudo cargar el chat de Kick. Cierra y vuelve a abrir Chat para reintentar.</body></html>","text/html","UTF-8",null);}
    @Override public void close(){closed=true;view.stopLoading();view.destroy();}
}
