package tv.duox.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

/**
 * Original 2D motion identity, drawn locally without network or video decoding.
 * The intro follows a fixed timeline (ms) that the intro sound is built around:
 * "Duo" slides in, two streams (Twitch purple, Kick green) converge and ignite the "X" at 850 ms, "TV" fades in last.
 */
final class BrandMotionView extends View {
    static final int INTRO_MS=2900,HIT_MS=1130;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final boolean connection;private final int mode;static final int INTRO=0,CONNECTION=1,SEARCH=2;
    private long celebrateStart;private Runnable celebrateDone;
    private final Typeface heavy=Typeface.create("sans-serif-black",Typeface.NORMAL),bold=Typeface.create("sans-serif",Typeface.BOLD);
    private float phase,wDuo,wDu,wX,wTv;private Shader glow;private Bitmap xBmp;private float xScale;private final Paint bmpPaint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
    private Shader xShader;
    Runnable afterFirstFrame;
    private final ValueAnimator motion=ValueAnimator.ofFloat(0,1);
    BrandMotionView(Context context,boolean connection){this(context,connection?CONNECTION:INTRO);}
    /** The logo while the app syncs several VOD: still, with a breathing X. */
    static BrandMotionView searching(Context context){return new BrandMotionView(context,SEARCH);}
    private BrandMotionView(Context context,int mode){super(context);this.mode=mode;this.connection=mode==CONNECTION;motion.setDuration(connection?2800:mode==SEARCH?2600:INTRO_MS);motion.setRepeatCount(connection||mode==SEARCH?ValueAnimator.INFINITE:0);motion.setInterpolator(new LinearInterpolator());motion.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(mode!=INTRO)setLayerType(LAYER_TYPE_HARDWARE,null);}
    void running(boolean enabled){if(enabled&&!motion.isStarted())motion.start();else if(!enabled)motion.cancel();}
    private static float ease(float v){float t=Math.max(0,Math.min(1,v));return 1-(float)Math.pow(1-t,3);}
    private static float seg(float t,float from,float to){return Math.max(0,Math.min(1,(t-from)/(to-from)));}
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);c.drawLine(x1,y1,x2,y2,paint);paint.setStyle(Paint.Style.FILL);}
    private void badge(Canvas c,float x,float y,String label,int color,float scale){c.save();c.translate(x,y);c.scale(scale,scale);paint.setColor(0x182effb7);c.drawCircle(0,0,52,paint);paint.setColor(0xff183b31);c.drawRoundRect(-44,-36,44,36,20,20,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(color);c.drawRoundRect(-44,-36,44,36,20,20,paint);paint.setStyle(Paint.Style.FILL);paint.setTypeface(bold);paint.setTextSize(label.equals("DuoX")?25:23);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(color);c.drawText(label,0,8,paint);c.restore();}
    private void measure(){
        if(xShader!=null)return;paint.setTypeface(heavy);paint.setTextSize(84);wDuo=paint.measureText("Duo");wDu=paint.measureText("Du");glow=new RadialGradient(0,0,100,new int[]{0xccd9ffe9,0x5053fc18,0x00a970ff},new float[]{0,.35f,1},Shader.TileMode.CLAMP);wX=paint.measureText("X");paint.setTypeface(bold);paint.setTextSize(22);wTv=paint.measureText("TV")+10;
        float left=-(wDuo+wX+wTv)/2;xShader=new LinearGradient(left+wDuo,-60,left+wDuo+wX,30,0xffa970ff,0xff53fc18,Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=mode==INTRO?getResources().getDisplayMetrics().density*600f/460f:Math.min(getWidth()/460f,getHeight()/190f);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(scale,scale);paint.setStyle(Paint.Style.FILL);paint.setShader(null);
        if(mode!=INTRO)for(int i=0;i<12;i++){double angle=i*Math.PI/6+phase*.5;float radius=65+(i%3)*17;paint.setColor(i%2==0?0x5053ffc0:0x4053fc18);float x=(float)Math.cos(angle)*radius*(connection?2:1.5f),y=(float)Math.sin(angle)*radius*.65f;canvas.drawCircle(x,y,1.3f+(float)Math.sin(phase*6.28+i)*.5f,paint);}
        if(mode==SEARCH){drawSearch(canvas);}
        else if(connection){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xff2a5745);path.reset();path.moveTo(-94,0);path.cubicTo(-35,-55,35,55,94,0);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<4;i++){float t=(phase+i*.25f)%1;float x=-94+188*t,y=(float)(-Math.sin(t*Math.PI*2)*20);paint.setColor(i%2==0?0xff53ffc0:0xff53fc18);canvas.drawCircle(x,y,3.5f,paint);}
            float bounce=(float)Math.sin(phase*Math.PI*2)*4;badge(canvas,-145,bounce,"DuoX",0xff53ffc0,1);badge(canvas,145,-bounce,"KICK",0xff53fc18,1);
            paint.setColor(0xff53ffc0);canvas.drawCircle(0,0,12,paint);paint.setColor(0xff103126);canvas.drawCircle(0,0,5+(float)Math.sin(phase*6.28)*1.5f,paint);
        }else{
            measure();drawIntro(canvas);
        }
        canvas.restore();
        if(afterFirstFrame!=null){Runnable start=afterFirstFrame;afterFirstFrame=null;postOnAnimation(start);}
    }
    /** Draws one frame of the intro at tMs on any canvas (used by IntroSurface's render thread). */
    void renderIntro(Canvas canvas,int w,int h,float tMs){renderIntro(canvas,w,h,tMs,true);}
    void renderIntro(Canvas canvas,int w,int h,float tMs,boolean background){
        phase=tMs/INTRO_MS;paint.setStyle(Paint.Style.FILL);paint.setShader(null);if(background)canvas.drawColor(0xff04080a);
        float scale=getResources().getDisplayMetrics().density*600f/460f;canvas.save();canvas.translate(w/2f,h/2f);canvas.scale(scale,scale);measure();drawIntro(canvas);canvas.restore();
    }
    /** The gradient X is rendered once into a bitmap: scaling live text that large re-rasterises the glyph every frame and stalls the Fire TV. */
    private void drawX(Canvas c,float left,int alpha){
        if(xBmp==null){xScale=getResources().getDisplayMetrics().density*600f/460f;int bw=(int)((wX+24)*xScale),bh=(int)(120*xScale);xBmp=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);Canvas bc=new Canvas(xBmp);bc.scale(xScale,xScale);bc.translate(12,90);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(heavy);p.setTextSize(84);p.setShader(new LinearGradient(0,-60,wX,30,0xffa970ff,0xff53fc18,Shader.TileMode.CLAMP));bc.drawText("X",0,0,p);}
        bmpPaint.setAlpha(alpha);float x=left+wDuo-12,y=26-90;c.drawBitmap(xBmp,null,new RectF(x,y,x+xBmp.getWidth()/xScale,y+xBmp.getHeight()/xScale),bmpPaint);
    }
    /** Intro timeline (ms): "Du" 300, "o" 800, breath of light 1050-1500, the X lands at HIT_MS with a flash, "TV" 1850, hold. Matches duox_signature.m4a. */
    private void drawIntro(Canvas canvas){
        float t=phase*INTRO_MS,left=-(wDuo+wX+wTv)/2,xCenter=left+wDuo+wX/2;
        float sinceHit=t-HIT_MS;
        if(sinceHit>0&&sinceHit<380){float k=1-sinceHit/320f;canvas.translate((float)Math.sin(sinceHit*.11)*5*k,(float)Math.cos(sinceHit*.13)*3*k);}
        paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(heavy);paint.setTextSize(84);
        // Warm-up: draw the X, the glow and the ring once, almost invisibly, so the GPU compiles them before the hit (no hitch on the flash).
        if(t<700){drawX(canvas,left,1);paint.setShader(glow);paint.setAlpha(1);canvas.save();canvas.translate(xCenter,-4);canvas.scale(.3f,.3f);canvas.drawCircle(0,0,100,paint);canvas.restore();paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(0x01d9ffe9);canvas.drawCircle(xCenter,-4,40,paint);paint.setStyle(Paint.Style.FILL);paint.setTypeface(bold);paint.setTextSize(22);paint.setColor(0x01a3b8b2);canvas.drawText("TV",left+wDuo+wX+10,26,paint);paint.setTypeface(heavy);paint.setTextSize(84);}
        float du=ease(seg(t,520,760)),o=ease(seg(t,830,1070));
        if(du>0){paint.setColor(((int)(du*255)<<24)|0xf3fff8);canvas.drawText("Du",left,26+(1-du)*16,paint);}
        if(o>0){paint.setColor(((int)(o*255)<<24)|0xf3fff8);canvas.drawText("o",left+wDu,26+(1-o)*16,paint);}
        float charge=seg(t,830,HIT_MS);
        if(charge>0&&sinceHit<0){paint.setShader(glow);paint.setAlpha((int)(120*charge));float r=.25f+.35f*charge;canvas.save();canvas.translate(xCenter,-4);canvas.scale(r,r);canvas.drawCircle(0,0,100,paint);canvas.restore();paint.setShader(null);}
        if(sinceHit>=0){
            float in=seg(sinceHit,0,240),back=1+2.70158f*(float)Math.pow(in-1,3)+1.70158f*(float)Math.pow(in-1,2);float sc=2.3f-1.3f*back;if(in>=1)sc=1;
            float fade=1-seg(sinceHit,0,900);
            if(fade>0){paint.setShader(glow);paint.setAlpha((int)(200*fade));float r=.7f+.55f*seg(sinceHit,0,900);canvas.save();canvas.translate(xCenter,-4);canvas.scale(r,r);canvas.drawCircle(0,0,100,paint);canvas.restore();paint.setShader(null);}
            canvas.save();canvas.scale(sc,sc,xCenter,-4);drawX(canvas,left,(int)(255*Math.min(1,in*3)));canvas.restore();
            float ring=seg(sinceHit,0,700);if(ring>0&&ring<1){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f*(1-ring)+.4f);paint.setColor(((int)((1-ring)*170)<<24)|0xd9ffe9);canvas.drawCircle(xCenter,-4,20+95*ease(ring),paint);paint.setStyle(Paint.Style.FILL);}
        }
        float tv=ease(seg(t,1500,1850));
        if(tv>0){paint.setTypeface(bold);paint.setTextSize(22);paint.setColor(((int)(tv*160)<<24)|0xa3b8b2);canvas.drawText("TV",left+wDuo+wX+10,26,paint);}
    }
    /** Plays the "found it" flourish, then calls done. */
    void celebrate(Runnable done){celebrateStart=System.nanoTime();celebrateDone=done;invalidate();}
    private void drawSearch(Canvas canvas){
        // Deliberately calm: the logo stays still, the X breathes and a thin line moves underneath.
        measure();float left=-(wDuo+wX+wTv)/2,xCenter=left+wDuo+wX/2;paint.setStyle(Paint.Style.FILL);paint.setTypeface(heavy);paint.setTextSize(84);paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xfff3fff8);canvas.drawText("Duo",left,26,paint);paint.setTypeface(bold);paint.setTextSize(22);paint.setColor(0x99a3b8b2);canvas.drawText("TV",left+wDuo+wX+10,26,paint);
        float t=celebrateStart>0?Math.min(1,(System.nanoTime()-celebrateStart)/500_000_000f):0;
        float breathe=1+.05f*(float)Math.sin(phase*Math.PI*2)+(celebrateStart>0?.2f*(1-ease(t)):0);
        canvas.save();canvas.scale(breathe,breathe,xCenter,-24);paint.setTypeface(heavy);paint.setTextSize(84);paint.setShader(xShader);canvas.drawText("X",left+wDuo,26,paint);paint.setShader(null);canvas.restore();
        if(celebrateStart>0){paint.setColor(((int)((1-t)*110)<<24)|0xd9ffe9);canvas.drawCircle(xCenter,-24,20+60*t,paint);}
        else{float bar=(phase*1.2f)%1;float from=left+(wDuo+wX)*bar;line(canvas,Math.max(left,from-40),52,Math.min(left+wDuo+wX,from+40),52,0x8853ffc0,3);}
        if(celebrateStart>0&&t>=1&&celebrateDone!=null){Runnable done=celebrateDone;celebrateDone=null;post(done);}
        if(celebrateStart>0&&t<1)postInvalidateOnAnimation();
    }
    @Override protected void onDetachedFromWindow(){running(false);super.onDetachedFromWindow();}
}
