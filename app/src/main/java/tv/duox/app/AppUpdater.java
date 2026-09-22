package tv.duox.app;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;

/** DuoX TV — DanikDeJesus. Public releases only, without account credentials. */
@androidx.media3.common.util.UnstableApi
final class AppUpdater {
    private static final String REPO="danikdejesus1/DuoX-TV";
    private static final String API="https://api.github.com/repos/"+REPO+"/releases/latest";
    private static final long MAX_APK=150L*1024*1024;
    private final MainActivity a;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(5,TimeUnit.MINUTES).build();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Call call;
    private Dialog dialog;
    private TextView state,remote;
    private Button action;
    private PackageInfo installed;
    private String tag,url,digest;
    private long size;
    private File ready;
    AppUpdater(MainActivity activity){a=activity;}
    /** One-shot, dialog-less check (used once per app launch to light up the ↻ icon); never polls again on its own. */
    static void peek(MainActivity a,java.util.function.Consumer<Boolean> result){
        a.net.execute(()->{
            boolean available=false;
            try{
                PackageInfo installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0);
                OkHttpClient http=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).build();
                Request req=new Request.Builder().url(API).header("User-Agent","DuoX/"+installed.versionName).header("Accept","application/vnd.github+json").build();
                try(Response response=http.newCall(req).execute()){
                    if(response.isSuccessful()&&response.body()!=null){
                        JSONObject release=new JSONObject(response.body().string());
                        if(!release.optBoolean("draft")&&!release.optBoolean("prerelease")){
                            String version=release.getString("tag_name");
                            boolean newer=UpdateVersion.compare(version,installed.versionName.replaceFirst("-.*$",""))>0;
                            if(newer){
                                JSONArray assets=release.getJSONArray("assets");
                                for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);
                                    if("DuoX.apk".equals(asset.optString("name"))&&"uploaded".equals(asset.optString("state"))){
                                        String u=asset.optString("browser_download_url","");long sz=asset.optLong("size",0);
                                        if(u.startsWith("https://github.com/"+REPO+"/releases/download/"+version+"/")&&u.endsWith("/DuoX.apk")&&sz>0&&sz<=MAX_APK)available=true;
                                        break;
                                    }}
                            }
                        }
                    }
                }
            }catch(Exception e){android.util.Log.w("DuoXUpdate","peek failed: "+e);}
            final boolean found=available;a.main.post(()->result.accept(found));
        });
    }

    void close(){if(dialog!=null)dialog.dismiss();worker.shutdownNow();}
    private boolean visible(){return !a.isFinishing()&&!a.isDestroyed()&&dialog.isShowing();}
    private void ui(Runnable task){a.runOnUiThread(()->{if(visible())task.run();});}
    void show(){
        try{installed=a.getPackageManager().getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES);}catch(Exception e){return;}
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=a.col();panel.setPadding(a.dp(20),a.dp(16),a.dp(20),a.dp(18));panel.setBackground(a.outline(0xed192b2a,20,0x664b7061));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(a.text(a.getString(R.string.update_title),20,MainActivity.MINT),new LinearLayout.LayoutParams(0,a.dp(38),1));
        Button close=a.subtle("×",dialog::dismiss);close.setContentDescription(a.getString(R.string.ui_069));header.addView(close,new LinearLayout.LayoutParams(a.dp(36),a.dp(34)));panel.addView(header);
        panel.addView(a.text(a.getString(R.string.update_installed,installed.versionName),15,MainActivity.WHITE));
        panel.addView(a.text(a.getString(R.string.update_built,date(a.getString(R.string.build_date))),11,MainActivity.MUTED));
        remote=a.text("",13,MainActivity.WHITE);remote.setPadding(0,a.dp(14),0,0);panel.addView(remote);
        state=a.text(a.getString(R.string.update_checking),12,MainActivity.MUTED);state.setPadding(0,a.dp(10),0,a.dp(14));panel.addView(state);
        action=a.subtle(a.getString(R.string.update_retry),this::check);panel.addView(action,new LinearLayout.LayoutParams(-1,a.dp(40)));
        dialog.setContentView(panel);dialog.setOnDismissListener(d->{Call active=call;if(active!=null)active.cancel();worker.shutdownNow();});dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setDimAmount(.3f);dialog.getWindow().setLayout(a.dp(390),-2);close.requestFocus();check();
    }
    private String date(String raw){try{return java.time.LocalDate.parse(raw.substring(0,10)).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(a.getResources().getConfiguration().getLocales().get(0)));}catch(Exception e){return "—";}}
    private Request request(String endpoint){return new Request.Builder().url(endpoint).header("User-Agent","DuoX/"+installed.versionName).header("Accept","application/vnd.github+json").build();}
    private void check(){
        action.setEnabled(false);remote.setText("");state.setText(R.string.update_checking);
        worker.execute(()->{try{
            call=http.newCall(request(API));JSONObject release;
            try(Response response=call.execute()){if(!response.isSuccessful()||response.body()==null)throw new IOException();release=new JSONObject(response.body().string());}
            if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException();
            String version=release.getString("tag_name");boolean newer=UpdateVersion.compare(version,installed.versionName.replaceFirst("-.*$",""))>0;
            String foundUrl="",foundDigest="";long foundSize=0;JSONArray assets=release.getJSONArray("assets");
            for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if("DuoX.apk".equals(asset.optString("name"))&&"uploaded".equals(asset.optString("state"))){foundUrl=asset.getString("browser_download_url");foundSize=asset.getLong("size");foundDigest=asset.optString("digest","");break;}}
            // Only download the APK belonging to this project's stable release.
            boolean valid=foundUrl.startsWith("https://github.com/"+REPO+"/releases/download/"+version+"/")&&foundUrl.endsWith("/DuoX.apk")&&foundSize>0&&foundSize<=MAX_APK;
            tag=version;url=foundUrl;digest=foundDigest;size=foundSize;
            String published=release.optString("published_at");
            ui(()->{remote.setText(a.getString(R.string.update_latest,version,date(published)));state.setText(newer?(valid?R.string.update_available:R.string.update_no_apk):R.string.update_current);action.setEnabled(true);action.setText(newer&&valid?R.string.update_download:R.string.update_retry);action.setOnClickListener(v->{if(newer&&valid)download();else check();});if(newer&&valid)action.requestFocus();});
        }catch(Exception e){android.util.Log.w("DuoXUpdate","check failed: "+e);ui(()->{state.setText(R.string.update_error);action.setEnabled(true);action.setText(R.string.update_retry);action.setOnClickListener(v->check());});}});
    }
    private void download(){
        action.setEnabled(false);state.setText(a.getString(R.string.update_progress,0));
        worker.execute(()->{File part=null;try{
            File dir=new File(a.getCacheDir(),"updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException();
            part=File.createTempFile("release-",".apk",dir);File target=new File(dir,"DuoX.apk");
            call=http.newCall(request(url));MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;
            try(Response response=call.execute()){
                if(!response.isSuccessful()||response.body()==null)throw new IOException();
                try(InputStream input=response.body().byteStream();OutputStream output=new FileOutputStream(part)){
                    byte[] buffer=new byte[65536];int read;
                    while((read=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=read;if(total>size||total>MAX_APK)throw new IOException();output.write(buffer,0,read);hash.update(buffer,0,read);int percent=(int)(total*100/size);if(percent!=previous){previous=percent;ui(()->state.setText(a.getString(R.string.update_progress,percent)));}}
                }
            }
            if(total!=size)throw new IOException();
            StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
            if(digest.startsWith("sha256:")&&!digest.substring(7).equalsIgnoreCase(hex.toString()))throw new IOException();
            ui(()->state.setText(R.string.update_verify));verify(part);
            if(target.exists()&&!target.delete())throw new IOException();if(!part.renameTo(target))throw new IOException();ready=target;
            ui(()->{state.setText(R.string.update_ready);action.setText(R.string.update_install);action.setEnabled(true);action.setOnClickListener(v->install());install();});
        }catch(Exception e){android.util.Log.w("DuoXUpdate","download failed: "+e);if(part!=null)part.delete();ui(()->{state.setText(R.string.update_failed);action.setEnabled(true);action.setText(R.string.update_download);action.setOnClickListener(v->download());});}});
    }
    /** Signing certificates of a package. Android 9 (Fire OS 7) leaves signingInfo empty for archives, so fall back to signatures. */
    private static Set<String> signers(PackageInfo info){
        Set<String> out=new HashSet<>();
        if(info.signingInfo!=null){for(android.content.pm.Signature s:info.signingInfo.getApkContentsSigners())out.add(s.toCharsString());}
        else if(info.signatures!=null){for(android.content.pm.Signature s:info.signatures)out.add(s.toCharsString());}
        return out;
    }
    private void verify(File apk)throws Exception{
        PackageInfo candidate=a.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES);
        if(candidate==null||!a.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()<=installed.getLongVersionCode()||UpdateVersion.compare(candidate.versionName,tag)!=0)throw new IOException();
        Set<String> expected=signers(installed),actual=signers(candidate);
        if(expected.isEmpty()||!expected.equals(actual))throw new IOException();
    }
    private void install(){
        try{
            if(!a.getPackageManager().canRequestPackageInstalls()){
                state.setText(R.string.update_permission);
                try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(android.content.ActivityNotFoundException e){a.startActivity(new Intent(Settings.ACTION_SETTINGS));}
                return;
            }
            if(ready==null||!ready.isFile())throw new IOException();
            Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".updates",ready);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(intent);
        }catch(Exception e){state.setText(R.string.update_failed);}
    }
}
