package tv.duox.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.function.BooleanSupplier;

/**
 * The opening animation on its own thread: it keeps moving smoothly while the main thread builds the Home behind it.
 * Timeline: the clock starts when the sound is ready, the last frame is held until the Home is ready (release), then it fades out.
 */
final class IntroSurface extends SurfaceView implements SurfaceHolder.Callback {
    private static final int FADE_MS=260;
    private final BrandMotionView art;
    private volatile boolean stop,release,skip,finished;
    private Thread thread;
    // Loop state survives a detach/re-attach of the view (the Home rebuild moves the cover to a new root).
    private long t0=-1,fadeFrom=-1,waitFrom=-1;private boolean fadeAnnounced,started;private volatile boolean homeDrawn;
    Runnable onStart,onSoundStart,onNearEnd,onFadeStart,onDone;private boolean nearAnnounced;
    BooleanSupplier soundReady=()->true;

    IntroSurface(Context context){
        super(context);art=new BrandMotionView(context,false);
        setZOrderOnTop(true);getHolder().setFormat(PixelFormat.TRANSLUCENT);getHolder().addCallback(this);
    }
    /** The Home is ready: the held last frame may fade out. */
    void release(){release=true;}
    /** The Home has been drawn once under the cover: revealing it will not hitch. */
    void homeDrawn(){homeDrawn=true;}
    /** Leave now (Back pressed). */
    void skip(){skip=true;release=true;}
    void shutdown(){stop=true;release=true;}

    @Override public void surfaceCreated(SurfaceHolder holder){stop=false;thread=new Thread(this::loop,"duox-intro");thread.start();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){
        stop=true;Thread t=thread;thread=null;
        if(t!=null)try{t.join(300);}catch(InterruptedException ignored){}
    }
    private void done(){if(finished)return;finished=true;if(onDone!=null)onDone.run();}

    private void loop(){
        if(!started){started=true;waitFrom=SystemClock.uptimeMillis();if(onStart!=null)onStart.run();}
        SurfaceHolder holder=getHolder();
        while(!stop){
            Canvas canvas=null;
            try{canvas=Build.VERSION.SDK_INT>=26?holder.lockHardwareCanvas():holder.lockCanvas();}catch(Exception ignored){}
            if(canvas==null){try{Thread.sleep(8);}catch(InterruptedException e){return;}continue;}
            long now=SystemClock.uptimeMillis();
            if(t0<0&&(soundReady.getAsBoolean()||now-waitFrom>700)){t0=now;if(onSoundStart!=null)onSoundStart.run();}
            float t=t0<0?0:now-t0;
            if(skip&&t<BrandMotionView.INTRO_MS)t=BrandMotionView.INTRO_MS;
            if(!nearAnnounced&&t>=BrandMotionView.INTRO_MS-1050){nearAnnounced=true;if(onNearEnd!=null)onNearEnd.run();}
            boolean holding=t>=BrandMotionView.INTRO_MS;if(holding)t=BrandMotionView.INTRO_MS;
            try{
                if(holding&&release&&(homeDrawn||skip||stop)){
                    if(fadeFrom<0){fadeFrom=now;}
                    float k=(now-fadeFrom)/(float)FADE_MS;
                    canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                    if(k<1){int w=canvas.getWidth(),h=canvas.getHeight(),alpha=(int)(255*(1-k));canvas.drawColor((alpha<<24)|0x04080a);
                        // only the logo area goes through an offscreen layer (a full-screen layer stalls this GPU for hundreds of ms)
                        int layer=canvas.saveLayerAlpha(w/2f-520,h/2f-230,w/2f+520,h/2f+230,alpha);art.renderIntro(canvas,w,h,t,false);canvas.restoreToCount(layer);}
                    if(!fadeAnnounced){fadeAnnounced=true;if(onFadeStart!=null)onFadeStart.run();}
                    holder.unlockCanvasAndPost(canvas);canvas=null;
                    if(k>=1){done();return;}
                }else art.renderIntro(canvas,canvas.getWidth(),canvas.getHeight(),t);
            }finally{if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}catch(Exception ignored){}}
            if(holding&&!(release&&homeDrawn))try{Thread.sleep(10);}catch(InterruptedException e){return;}
        }
    }
}
