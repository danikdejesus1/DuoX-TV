/**
 * DuoX TV — Creado por DanikDeJesus
 * Copyright (c) 2026 DanikDeJesus.
 * Firma de autor: no modifica el funcionamiento de la aplicación.
 */
package tv.duox.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.TextUtils;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import okhttp3.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    static final int BG=0xff0c1418,PANEL=0xff19282b,MINT=0xff69ffb4,WHITE=0xfff3fff8,MUTED=0xff9fb5af;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService net=Executors.newSingleThreadExecutor(),images=Executors.newFixedThreadPool(3);
    final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap v){return v.getByteCount();}};
    AppUpdater updater;boolean updateChecked;int updateAvailable;ImageView updatesIcon;Button languageButton;
    void updates(){if(updater!=null)updater.close();updater=new AppUpdater(this);updater.show();updateAvailable=0;}
    /** Lights up green only after the one silent check on this app launch found a newer stable release. */
    void tintUpdateIcon(Button b){
        Drawable[] cd=b.getCompoundDrawables();if(cd[0]==null)return;cd[0].mutate().setColorFilter(updateAvailable==1?MINT:0xff9fb5af,android.graphics.PorterDuff.Mode.SRC_IN);b.invalidate();
    }

    KickSession kickLoader;FrameLayout heroVisual;
    KickChat kickChat;EmoteChat chat;android.webkit.WebView chatText;int chatVersion;
    TwitchAccount account;SharedPreferences prefs;FrameLayout root,hero;LinearLayout rail,controls;TextView status,heroTitle,heroMeta,heroName;ImageView heroImage,pause,favoriteTool;Button watch;TextView audioCaption;SearchScreen searchScreen;View chatQrBox;long followedAt;boolean homeReady,keepIntro,deferFill;volatile List<TwitchAccount.Stream> prefetchFollowed,prefetchDirectory;volatile long prefetchAt;final List<String> railOrder=new ArrayList<>();String railSignature="";
    final Map<String,TwitchAccount.Stream> directory=new LinkedHashMap<>();LinearLayout offlineRow;TextView offlineHeading;boolean loadingDirectory,browsingVods,vodMode;long directoryUpdated;String libraryChannel="",shelfSignature="";
    FrameLayout livePanel;Runnable livePanelEnter;boolean skipEntryPanel,liveRefreshing,warming;final Runnable livePanelHide=()->closeLivePanel();
    List<TwitchAccount.Stream> streams=new ArrayList<>();TwitchAccount.Stream selected;
    boolean profileLive;boolean previewMuted=true;Button previewAudio;
    ExoPlayer preview;PlayerView previewView;int previewVersion;String previewLogin="";boolean foreground=true;Runnable previewJob;
    final List<Pane> panes=new ArrayList<>();LinearLayout videoGrid;ImageView channelIcon;TextView channelHeading;int activePane;
    final class Pane {String login;ExoPlayer engine;DefaultTrackSelector selector;FrameLayout box;PlayerView view;TextView label;int capW=Integer.MAX_VALUE,capH=Integer.MAX_VALUE;}
    /** Synced VOD: pane i shows the same moment as pane 0 when its position equals pane 0 position + syncOffsets[i]. */
    long[] syncOffsets=new long[0];List<String> syncIds;String syncTitle,syncThumb,syncNames;boolean synced,aligning;TextView syncNotice;int adjustStep=1000;
    FrameLayout syncOverlay;TextView syncOverlayText;BrandMotionView syncLogo;java.util.concurrent.atomic.AtomicBoolean syncCancel=new java.util.concurrent.atomic.AtomicBoolean();List<String> syncKeys,syncUrls;long[] syncStarts;
    ExoPlayer player;DefaultTrackSelector tracks;String channel="";boolean playing,destroyed,refreshing,paused;int screenVersion,requestVersion;volatile int authVersion;Dialog authDialog;long authDeadline;int pollInterval=5;
    VodHistory vodHistory;VodHistory.Entry activeVod;long lastVodSave;LinearLayout resumeSection;View resumeFallback;
    SeekBar vodSeek;TextView vodTime;boolean scrubbing;
    final Runnable vodTick=new Runnable(){public void run(){if(!playing||!vodMode||vodSeek==null)return;updateVodProgress();if(SystemClock.elapsedRealtime()-lastVodSave>=10000)saveVodProgress();main.postDelayed(this,500);}};
    final Set<Integer> held=new HashSet<>();int revealKey=-1;boolean touching;
    final Runnable hide=new Runnable(){public void run(){if(!playing||controls==null)return;if(!hasWindowFocus()||!held.isEmpty()||touching){scheduleHide();return;}controls.setVisibility(View.GONE);syncLabels();}};
    final LiveTransitions favoriteStates=new LiveTransitions();boolean checkingFavorites;final java.util.ArrayDeque<String> liveNotices=new java.util.ArrayDeque<>();View liveNotice;
    final Runnable favoriteTick=new Runnable(){public void run(){if(destroyed||!foreground)return;checkFavorites();main.postDelayed(this,60000);}};
    final Runnable dismissNotice=()->{if(liveNotice!=null){((android.view.ViewGroup)liveNotice.getParent()).removeView(liveNotice);liveNotice=null;}showNextNotice();};
    void checkFavorites(){
        Set<String> saved=new TreeSet<>(prefs.getStringSet("favorites",Collections.emptySet()));favoriteStates.retain(saved);if(checkingFavorites||saved.isEmpty())return;checkingFavorites=true;
        net.execute(()->{Map<String,Boolean> observed=new HashMap<>();try{List<String> twitch=new ArrayList<>();for(String key:saved)if(!ChannelKey.kick(key))twitch.add(key);
            if(!twitch.isEmpty()&&account.connected())try{Set<String> live=account.liveFavorites(twitch);for(String key:twitch)observed.put(key,live.contains(key));}catch(Exception ignored){}
            for(String key:saved)if(ChannelKey.kick(key))try{observed.put(key,KickSource.isLive(key));}catch(Exception ignored){}
        }finally{main.post(()->{checkingFavorites=false;if(destroyed||!foreground)return;Set<String> current=prefs.getStringSet("favorites",Collections.emptySet());for(Map.Entry<String,Boolean> item:observed.entrySet())if(current.contains(item.getKey())&&favoriteStates.observe(item.getKey(),item.getValue())&&playing){boolean watching=false;for(Pane pane:panes)if(pane.login.equals(item.getKey()))watching=true;if(!watching)liveNotices.add(item.getKey());}showNextNotice();});}});
    }
    void clearNotices(){main.removeCallbacks(dismissNotice);liveNotices.clear();if(liveNotice!=null){android.view.ViewParent parent=liveNotice.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(liveNotice);liveNotice=null;}}
    void showNextNotice(){if(liveNotice!=null)return;if(!playing||!foreground){liveNotices.clear();return;}while(!liveNotices.isEmpty()){
        String key=liveNotices.remove();boolean watching=false;for(Pane pane:panes)if(pane.login.equals(key))watching=true;if(watching||!prefs.getStringSet("favorites",Collections.emptySet()).contains(key))continue;
        LinearLayout bubble=new LinearLayout(this);bubble.setGravity(Gravity.CENTER_VERTICAL);bubble.setPadding(dp(14),dp(12),dp(16),dp(12));bubble.setBackground(outline(0xeb142a25,18,ChannelKey.color(key)));bubble.setElevation(dp(10));bubble.setFocusable(false);
        ImageView badge=platformBadge(key);bubble.addView(badge,new LinearLayout.LayoutParams(dp(28),dp(28)));TwitchAccount.Stream info=streamInfo(key);TextView message=text((info==null?ChannelKey.slug(key):info.name)+getString(R.string.ui_102),15,WHITE);message.setMaxLines(2);message.setEllipsize(TextUtils.TruncateAt.END);message.setPadding(dp(12),0,0,0);bubble.addView(message,new LinearLayout.LayoutParams(0,-2,1));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(340),-2,Gravity.TOP|Gravity.RIGHT);bp.setMargins(0,dp(20),dp(20),0);root.addView(bubble,bp);liveNotice=bubble;main.postDelayed(dismissNotice,5000);break;
    }}
    final Runnable refreshTick=new Runnable(){public void run(){if(destroyed)return;if(!playing&&!browsingVods)refresh();else if(account.connected())net.execute(()->{try{account.validateSession();}catch(Exception ignored){}});main.postDelayed(this,60000);}};
    @Override protected void attachBaseContext(android.content.Context base){android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());config.setLocale(new java.util.Locale(base.getSharedPreferences("duox",0).getString("interface_language","es")));super.attachBaseContext(base.createConfigurationContext(config));}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);prefs=getSharedPreferences("duox",0);account=new TwitchAccount(this);vodHistory=VodHistory.decode(prefs.getString("vod_history_v1",""));streams.addAll(KickSession.cached(this));loadCatalog();loadLiveCache();startupActive=state==null;
        // On a fresh start the intro is shown immediately and the Home is built behind it, so the first frame does not wait for layout work.
        if(startupActive){root=new FrameLayout(this);root.setBackgroundColor(0xff080f12);setContentView(root);showIntro();}else home();net.execute(this::cleanOldModels);}
    final java.util.LinkedHashMap<String,TwitchAccount.Stream> profileCache=new java.util.LinkedHashMap<>();
    FrameLayout introCover;IntroSurface introSurface;android.media.MediaPlayer introSound;
    boolean startupActive,introFrameVisible,introSoundReady;
    final Runnable endIntro=this::finishIntro;
    final Runnable buildHome=()->{if(introCover==null||homeReady)return;keepIntro=true;home();keepIntro=false;finishIntro();};
    /** Network answers that arrive during the intro are applied right after it, so the animation never competes with layout work. */
    final List<Runnable> introQueue=new ArrayList<>();
    void ui(Runnable task){main.post(()->{if(startupActive)introQueue.add(task);else task.run();});}
    /** The audio marker on a multiview pane is temporary: it shows at start and on every change, then fades. */
    final Runnable fadeAudio=()->{if(destroyed||activePane>=panes.size())return;Pane item=panes.get(activePane);Drawable ring=item.box.getBackground();if(ring==null)return;android.animation.ValueAnimator fade=android.animation.ValueAnimator.ofInt(255,0).setDuration(700);fade.addUpdateListener(a->ring.setAlpha((int)a.getAnimatedValue()));fade.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(item.box.getBackground()==ring){item.box.setBackgroundColor(Color.BLACK);item.label.setText(ChannelKey.label(item.login));}}});fade.start();};
    void showIntro(){
        introCover=new FrameLayout(this);introCover.setElevation(dp(40));introCover.setBackgroundColor(0xff04080a);introCover.setClickable(true);introCover.setFocusable(true);
        // The animation lives on its own thread (IntroSurface): it keeps moving while the main thread builds the Home behind it.
        introSurface=new IntroSurface(this);introCover.addView(introSurface,new FrameLayout.LayoutParams(-1,-1));/* on the window itself, not in the Home's root: rebuilding the Home must never detach the animation surface */((ViewGroup)getWindow().getDecorView()).addView(introCover,new FrameLayout.LayoutParams(-1,-1));introCover.requestFocus();
        introSurface.soundReady=()->introSoundReady;
        introSurface.onSoundStart=()->{android.media.MediaPlayer s=introSound;if(s!=null&&introSoundReady)try{s.start();}catch(Exception ignored){}};
        introSurface.onStart=()->main.post(this::buildHomeBehind);
        introSurface.onNearEnd=()->main.post(()->{if(root==null)return;root.setVisibility(View.VISIBLE);/* let the Home draw its first frames under the cover before the cover fades */root.postOnAnimation(()->root.postOnAnimation(()->main.postDelayed(()->{if(introSurface!=null)introSurface.homeDrawn();},300)));});
        introSurface.onFadeStart=()->main.post(()->{if(introCover!=null)introCover.setBackgroundColor(0);});
        introSurface.onDone=()->main.post(this::afterIntro);
        try{
            android.media.MediaPlayer sound=new android.media.MediaPlayer();introSound=sound;
            try(android.content.res.AssetFileDescriptor fd=getResources().openRawResourceFd(R.raw.duox_signature)){sound.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
            sound.setVolume(.6f,.6f);sound.setOnPreparedListener(ready->{if(introSound==ready)introSoundReady=true;});sound.prepareAsync();
        }catch(Exception ignored){if(introSound!=null){introSound.release();introSound=null;}}
    }
    /** Builds the Home under the animation; the animation is released to fade out once the Home is ready. */
    void buildHomeBehind(){
        if(introCover==null||introSurface==null)return;introFrameVisible=true;startPrefetch();
        if(!homeReady){keepIntro=true;deferFill=true;home();deferFill=false;keepIntro=false;/* not drawn (no GPU work) while the animation plays: it would starve the animation's frames */root.setVisibility(View.INVISIBLE);}else introSurface.release();
    }
    void afterIntro(){
        if(introCover==null&&introSurface==null)return;
        if(!homeReady){keepIntro=true;home();keepIntro=false;}
        if(root!=null)root.setVisibility(View.VISIBLE);closeIntro();
        if(foreground&&!destroyed&&!playing&&!browsingVods){main.postDelayed(()->{if(foreground&&!destroyed&&!playing&&!browsingVods){startPreview();main.removeCallbacks(favoriteTick);main.post(favoriteTick);}},900);}
    }
    void finishIntro(){
        if(introCover==null)return;if(!homeReady){keepIntro=true;home();keepIntro=false;}
        if(introSurface!=null)introSurface.skip();else afterIntro();
    }
    void closeIntro(){main.removeCallbacks(endIntro);main.removeCallbacks(buildHome);introSoundReady=false;if(introSound!=null){try{introSound.release();}catch(Exception ignored){}introSound=null;}if(introSurface!=null){IntroSurface surface=introSurface;introSurface=null;surface.onDone=null;surface.shutdown();}if(introCover!=null){introCover.animate().cancel();android.view.ViewParent parent=introCover.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(introCover);introCover=null;startupActive=false;introFrameVisible=false;if(watch!=null&&!playing)watch.requestFocus();}if(!startupActive&&!introQueue.isEmpty()){List<Runnable> pending=new ArrayList<>(introQueue);introQueue.clear();main.postDelayed(()->{if(!destroyed)for(Runnable task:pending)task.run();},450);}}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    /** Thin circular ring in the platform color; the picture inside is clipped to the circle and fills it. */
    GradientDrawable ring(int fill,int stroke,int width){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(fill);d.setStroke(dp(width),stroke);return d;}
    GradientDrawable outline(int color,int radius,int stroke){GradientDrawable d=shape(color,radius);d.setStroke(dp(2),stroke);return d;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    Button button(String title,Runnable run){Button b=new Button(this);b.setId(View.generateViewId());b.setText(title);b.setTextSize(14);b.setTextColor(WHITE);b.setAllCaps(false);b.setPadding(dp(18),dp(5),dp(18),dp(5));b.setBackground(shape(PANEL,12));b.setMinHeight(dp(44));b.setOnClickListener(v->run.run());b.setOnFocusChangeListener((v,f)->{b.setBackground(f?outline(0xff234439,12,MINT):shape(PANEL,12));b.setTextColor(f?MINT:WHITE);});return b;}
    void marginAdd(LinearLayout row,View v,int w,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.setMargins(0,0,dp(10),0);row.addView(v,p);}
    Button subtle(String title,Runnable run){Button b=button(title,run);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(shape(0x221f3534,10));b.setOnFocusChangeListener((v,f)->{b.setBackground(f?outline(0xff234439,10,MINT):shape(0x221f3534,10));b.setTextColor(f?MINT:MUTED);});return b;}
    void compactSheet(String title,String[] labels,java.util.function.IntConsumer action){
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=col();panel.setPadding(dp(20),dp(18),dp(20),dp(18));panel.setBackground(outline(0xe6192b2a,20,0x664b7061));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(title,21,MINT);header.addView(heading,new LinearLayout.LayoutParams(0,dp(40),1));Button close=subtle("×",dialog::dismiss);close.setContentDescription(getString(R.string.ui_069));header.addView(close,new LinearLayout.LayoutParams(dp(40),dp(36)));panel.addView(header);
        Button first=null;for(int i=0;i<labels.length;i++){final int index=i;Button item=button(labels[i],()->{dialog.dismiss();action.accept(index);});item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.topMargin=dp(8);panel.addView(item,rp);if(first==null)first=item;}
        dialog.setContentView(panel);dialog.show();dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setDimAmount(.25f);dialog.getWindow().setLayout(dp(420),-2);if(first!=null)first.requestFocus();
    }
    void accounts(){compactSheet(getString(R.string.ui_000),new String[]{"Kick  ·  "+(KickSession.connected(this)?getString(R.string.ui_001):getString(R.string.ui_002)),"Twitch  ·  "+(account.connected()?"@"+account.name():getString(R.string.ui_002))},i->{if(i==0)kickAccount();else accountDialog();});}
    /** Only Halloween exists so far; other seasons will get their own line (and their own intro) later. Returns null outside the window. */
    String seasonalTeaser(){
        java.util.Calendar c=java.util.Calendar.getInstance();int month=c.get(java.util.Calendar.MONTH),day=c.get(java.util.Calendar.DAY_OF_MONTH);
        boolean halloweenWindow=(month==java.util.Calendar.SEPTEMBER&&day>=15)||month==java.util.Calendar.OCTOBER;
        return halloweenWindow?getString(R.string.season_halloween):null;
    }
    /** Small, quiet dropdown right under the language icon — not the big centered sheet used elsewhere, since there are only two choices. */
    void languages(){
        if(languageButton==null){compactSheet(getString(R.string.ui_077),new String[]{"Español","English"},i->{prefs.edit().putString("interface_language",i==0?"es":"en").apply();recreate();});return;}
        LinearLayout panel=col();panel.setPadding(dp(5),dp(6),dp(5),dp(6));GradientDrawable frame=shape(0xf50e1a18,14);frame.setStroke(dp(1),0x33ffffff);panel.setBackground(frame);
        boolean isEn="en".equals(prefs.getString("interface_language","es"));
        Button es=softRow("Español",()->{prefs.edit().putString("interface_language","es").apply();recreate();});
        Button en=softRow("English",()->{prefs.edit().putString("interface_language","en").apply();recreate();});
        panel.addView(es,new LinearLayout.LayoutParams(-1,dp(36)));panel.addView(en,new LinearLayout.LayoutParams(-1,dp(36)));
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(panel);dialog.setOnDismissListener(d->scheduleHide());dialog.show();
        Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(0f);
        int width=dp(150),screenW=getResources().getDisplayMetrics().widthPixels;int[] loc=new int[2];languageButton.getLocationOnScreen(loc);
        w.setGravity(Gravity.TOP|Gravity.LEFT);WindowManager.LayoutParams lp=w.getAttributes();
        lp.x=Math.max(dp(8),Math.min(loc[0]+languageButton.getWidth()/2-width/2,screenW-width-dp(8)));lp.y=loc[1]+languageButton.getHeight()+dp(8);
        w.setAttributes(lp);w.setLayout(width,-2);(isEn?en:es).requestFocus();
    }
    void updatePreviewAudio(){previewAudio.setText("");android.graphics.drawable.Drawable icon=getDrawable(previewMuted?R.drawable.ic_volume_off:R.drawable.ic_volume_on);icon.setBounds(0,0,dp(22),dp(22));previewAudio.setCompoundDrawables(icon,null,null,null);previewAudio.setPadding(dp(10),0,dp(10),0);previewAudio.setContentDescription(previewMuted?getString(R.string.ui_010):getString(R.string.ui_011));}
    void home(){release();browsingVods=false;vodMode=false;HomeCatalog.sortLive(streams);railOrder.clear();railSignature="";if(selected==null&&!streams.isEmpty())selected=streams.get(0);playing=false;screenVersion++;requestVersion++;root=new FrameLayout(this);root.setBackgroundColor(0xff080f12);setContentView(root);
        LinearLayout body=col();body.setPadding(dp(120),dp(18),dp(30),dp(28));root.addView(body,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text("",34,WHITE);android.text.SpannableStringBuilder brandName=new android.text.SpannableStringBuilder("DuoX TV");brandName.setSpan(new android.text.style.ForegroundColorSpan(MINT),3,4,0);brandName.setSpan(new android.text.style.RelativeSizeSpan(.42f),5,7,0);brandName.setSpan(new android.text.style.ForegroundColorSpan(0x99a3b8b2),5,7,0);brand.setText(brandName);brand.setTypeface(null,Typeface.BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,dp(38),1));
        Button search=subtle(getString(R.string.ui_003),this::search);search.setCompoundDrawablesWithIntrinsicBounds(tv.duox.app.R.drawable.ic_search,0,0,0);search.setCompoundDrawablePadding(dp(8));search.setBackgroundColor(Color.TRANSPARENT);marginAdd(top,search,148,32);Button accountsButton=subtle(getString(R.string.ui_004),this::accounts);marginAdd(top,accountsButton,136,32);Button language=subtle("",this::languages);language.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_language,0,0,0);language.setGravity(Gravity.CENTER);language.setContentDescription(getString(R.string.ui_077)+" · "+prefs.getString("interface_language","es").toUpperCase(Locale.ROOT));marginAdd(top,language,46,32);languageButton=language;Button updates=subtle("",this::updates);updates.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_update,0,0,0);updates.setContentDescription(getString(R.string.update_title));marginAdd(top,updates,46,32);tintUpdateIcon(updates);
        if(!updateChecked){updateChecked=true;AppUpdater.peek(this,found->{updateAvailable=found?1:-1;if(playing||destroyed)return;android.view.View v=body.findViewWithTag("updatesBtn");if(v instanceof Button)tintUpdateIcon((Button)v);});}
        updates.setTag("updatesBtn");body.addView(top);
        TextView sub=text(getString(R.string.ui_005),10,MUTED);sub.setLetterSpacing(.15f);sub.setPadding(0,dp(4),0,dp(12));body.addView(sub);
        hero=new FrameLayout(this);hero.setBackground(shape(0x5e152b29,20));hero.setClipToOutline(true);body.addView(hero,new LinearLayout.LayoutParams(-1,0,1));
        heroVisual=new FrameLayout(this);heroVisual.setBackgroundColor(Color.BLACK);hero.addView(heroVisual,new FrameLayout.LayoutParams(dp(400),-1,Gravity.RIGHT));LinearLayout content=col();content.setPadding(dp(22),dp(15),dp(18),dp(14));hero.addView(content,new FrameLayout.LayoutParams(dp(500),-1,Gravity.LEFT));
        // The preview keeps a 16:9 frame, so streams fill it without bars, cropping or stretching.
        hero.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{int h=b-t,w=r-l;if(h<=0)return;int visual=Math.min(w-dp(300),h*16/9),copy=w-visual;FrameLayout.LayoutParams vp=(FrameLayout.LayoutParams)heroVisual.getLayoutParams(),cp=(FrameLayout.LayoutParams)content.getLayoutParams();if(vp.width!=visual||cp.width!=copy){vp.width=visual;cp.width=copy;heroVisual.setLayoutParams(vp);content.setLayoutParams(cp);}});
        heroName=text(getString(R.string.ui_006),27,MINT);heroName.setTypeface(null,Typeface.BOLD);heroName.setSingleLine();heroName.setEllipsize(TextUtils.TruncateAt.END);content.addView(heroName);
        heroTitle=text(getString(R.string.ui_007),14,WHITE);heroTitle.setMaxLines(3);heroTitle.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,-2);titleP.topMargin=dp(4);titleP.bottomMargin=dp(6);content.addView(heroTitle,titleP);
        heroMeta=text(getString(R.string.ui_008),11,MUTED);heroMeta.setSingleLine();heroMeta.setEllipsize(TextUtils.TruncateAt.END);content.addView(heroMeta);
        content.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));watch=subtle(getString(R.string.ui_009),()->{if(selected!=null)openChannel(selected.login);else accounts();});watch.setTextColor(MINT);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(148),dp(36));LinearLayout previewActions=new LinearLayout(this);content.addView(previewActions);previewActions.addView(watch,wp);previewAudio=subtle(getString(R.string.ui_010),()->{previewMuted=!previewMuted;if(preview!=null)preview.setVolume(previewMuted?0:1);updatePreviewAudio();});LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(42),dp(36));ap.setMargins(dp(8),0,0,0);previewActions.addView(previewAudio,ap);watch.setNextFocusRightId(previewAudio.getId());previewAudio.setNextFocusLeftId(watch.getId());updatePreviewAudio();
        // Direct focus paths: the top bar is one press up from the banner and the banner one press down from the bar.
        watch.setNextFocusUpId(search.getId());previewAudio.setNextFocusUpId(accountsButton.getId());search.setNextFocusDownId(watch.getId());accountsButton.setNextFocusDownId(watch.getId());language.setNextFocusDownId(previewAudio.getId());updates.setNextFocusDownId(previewAudio.getId());
        heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);heroVisual.addView(heroImage,new FrameLayout.LayoutParams(-1,-1));if(!deferFill){previewView=(PlayerView)getLayoutInflater().inflate(tv.duox.app.R.layout.home_preview,heroVisual,false);previewView.setVisibility(View.INVISIBLE);heroVisual.addView(previewView);}else previewView=null;
        offlineHeading=text("OFFLINE",15,MUTED);offlineHeading.setTypeface(null,Typeface.BOLD);offlineHeading.setPadding(0,dp(16),0,dp(8));body.addView(offlineHeading);offlineRow=new LinearLayout(this);body.addView(shelf(offlineRow),new LinearLayout.LayoutParams(-1,dp(88)));
        status=text("",11,MUTED);status.setPadding(0,dp(10),0,0);body.addView(status);
        LinearLayout dock=col();dock.setGravity(Gravity.CENTER_HORIZONTAL);dock.setBackground(outline(0xf219272a,28,0xff294139));dock.setElevation(dp(12));dock.setClipToOutline(true);dock.setPadding(dp(6),dp(12),dp(6),dp(10));TextView live=text("● LIVE",11,MINT);live.setGravity(Gravity.CENTER);live.setTypeface(null,Typeface.BOLD);dock.addView(live,new LinearLayout.LayoutParams(-1,dp(28)));
        ScrollView railScroll=new ScrollView(this);railScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);railScroll.setVerticalScrollBarEnabled(false);railScroll.setClipToPadding(true);rail=col();rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(4),dp(8),dp(4),dp(8));railScroll.addView(rail);dock.addView(railScroll,new LinearLayout.LayoutParams(-1,0,1));FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(88),-1,Gravity.LEFT);rp.setMargins(dp(18),dp(20),0,dp(20));root.addView(dock,rp);
        // Version (bottom-right) and the seasonal teaser (bottom-center) share the same baseline as the "N canales seguidos" status line below the shelves.
        TextView version=null;String v0=null;try{v0=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        if(v0!=null){version=text("v"+v0,10,0x668a9d96);FrameLayout.LayoutParams vp2=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);vp2.rightMargin=dp(10);root.addView(version,vp2);}
        final TextView versionLabel=version;
        // Seasonal teaser: a small line hinting at the themed intro coming later that month; kept in the DuoX voice (short, uppercase, letter-spaced).
        String season=seasonalTeaser();TextView teaser=null;
        if(season!=null){teaser=text(season,10,0x8869ffb4);teaser.setLetterSpacing(.08f);teaser.setSingleLine();FrameLayout.LayoutParams tp2=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);root.addView(teaser,tp2);}
        final TextView teaserLabel=teaser;
        if(versionLabel!=null||teaserLabel!=null){
            // Aligned by text baseline (not box center): the emoji in the teaser and the different font sizes have different line metrics, so centering the boxes left the text itself off by a few px.
            Runnable alignBottom=()->{if(status.getBaseline()<0)return;int[] sv=new int[2];status.getLocationOnScreen(sv);int[] rv=new int[2];root.getLocationOnScreen(rv);float baseline=(sv[1]-rv[1])+status.getBaseline();if(versionLabel!=null&&versionLabel.getBaseline()>=0)versionLabel.setY(baseline-versionLabel.getBaseline());if(teaserLabel!=null&&teaserLabel.getBaseline()>=0)teaserLabel.setY(baseline-teaserLabel.getBaseline());};
            status.addOnLayoutChangeListener((v,l,t,r,bo,ol,ot,orr,ob)->{if(t!=ot)alignBottom.run();});status.post(alignBottom);
        }
        shelfSignature="";
        if(deferFill){watch.requestFocus();homeReady=true;Runnable[] steps={this::renderRail,this::renderShelves,this::renderSelection};for(int i=0;i<steps.length;i++){final Runnable step=steps[i];final boolean last=i==steps.length-1;main.postDelayed(()->{if(!destroyed&&!playing&&!browsingVods)step.run();if(last){/* creating the Kick WebView is the slowest thing on the main thread: do it now, behind the animation, not after it */warming=true;refresh();refreshDirectory();warming=false;startPreview();}if(last&&introSurface!=null)main.postDelayed(()->{if(introSurface!=null)introSurface.release();},250);},80L*(i+1));}}
        else{renderRail();renderShelves();renderSelection();watch.requestFocus();refresh();refreshDirectory();homeReady=true;}
    }
    HorizontalScrollView shelf(LinearLayout row){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);row.setPadding(dp(3),dp(3),dp(3),dp(3));scroll.addView(row);scroll.setOnScrollChangeListener((View v,int x,int y,int ox,int oy)->loadVisibleAvatars(row));row.post(()->loadVisibleAvatars(row));return scroll;}
    ImageView platformBadge(String key){ImageView badge=new ImageView(this);badge.setImageResource(ChannelKey.kick(key)?tv.duox.app.R.drawable.ic_kick:tv.duox.app.R.drawable.ic_twitch);badge.setBackground(shape(0xff0c1418,5));badge.setPadding(dp(3),dp(3),dp(3),dp(3));badge.setContentDescription(ChannelKey.kick(key)?"Kick":"Twitch");return badge;}
    void loadVisibleAvatars(View v){if(v instanceof ImageView&&v.getTag() instanceof String&&((String)v.getTag()).startsWith("pending:")&&v.getGlobalVisibleRect(new Rect()))loadImage((ImageView)v,((String)v.getTag()).substring(8));if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)loadVisibleAvatars(g.getChildAt(i));}}
    void avatarInto(FrameLayout box,TwitchAccount.Stream info,int size){avatarInto(box,info,size,false);}
    void avatarInto(FrameLayout box,TwitchAccount.Stream info,int size,boolean lazy){ImageView image=new ImageView(this);image.setBackground(shape(0xff29443b,size/2));image.setClipToOutline(true);image.setScaleType(ImageView.ScaleType.CENTER_CROP);box.addView(image,new FrameLayout.LayoutParams(-1,-1));if(info!=null&&!info.avatar.isEmpty()){if(lazy)image.setTag("pending:"+info.avatar);else loadImage(image,info.avatar);}else{TextView letter=text(info==null||info.name.isEmpty()?"?":info.name.substring(0,1).toUpperCase(Locale.ROOT),20,WHITE);letter.setGravity(Gravity.CENTER);box.addView(letter,new FrameLayout.LayoutParams(-1,-1));}}
    View channelCard(String key,boolean offline){TwitchAccount.Stream info=streamInfo(key);LinearLayout card=col();card.setId(View.generateViewId());card.setGravity(Gravity.CENTER);card.setPadding(dp(6),dp(4),dp(6),dp(4));card.setFocusable(true);card.setClickable(true);String name=info==null?ChannelKey.slug(key):info.name;card.setContentDescription(name+", "+(ChannelKey.kick(key)?"Kick":"Twitch")+(offline?getString(R.string.ui_107):""));
        FrameLayout avatar=new FrameLayout(this);avatarInto(avatar,info,46,true);card.addView(avatar,new LinearLayout.LayoutParams(dp(46),dp(46)));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.BOTTOM|Gravity.RIGHT);avatar.addView(platformBadge(key),bp);if(offline){avatar.setAlpha(.48f);ColorMatrix gray=new ColorMatrix();gray.setSaturation(0);((ImageView)avatar.getChildAt(0)).setColorFilter(new ColorMatrixColorFilter(gray));}
        TextView label=text(name,11,offline?MUTED:WHITE);label.setGravity(Gravity.CENTER);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(22));lp.topMargin=dp(3);card.addView(label,lp);
        card.setOnFocusChangeListener((v,f)->card.setBackground(f?outline(0xff213b32,12,MINT):shape(Color.TRANSPARENT,12)));card.setOnClickListener(v->{if(offline)openLibrary(key);else openChannel(key);});return card;
    }
    void renderShelves(){if(playing||browsingVods||offlineRow==null)return;List<TwitchAccount.Stream> offline=HomeCatalog.offline(directory.values(),streams);StringBuilder sig=new StringBuilder(recentChannels().toString());for(TwitchAccount.Stream item:directory.values())sig.append(item.login).append(item.avatar);for(TwitchAccount.Stream item:streams)sig.append(item.login);if(sig.toString().equals(shelfSignature))return;shelfSignature=sig.toString();
        offlineRow.removeAllViews();offlineHeading.setText("OFFLINE");View firstCard=null;for(TwitchAccount.Stream item:offline){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(76),dp(82));cp.rightMargin=dp(2);View card=channelCard(item.login,true);card.setNextFocusUpId(watch.getId());offlineRow.addView(card,cp);if(firstCard==null)firstCard=card;}if(firstCard!=null){watch.setNextFocusDownId(firstCard.getId());previewAudio.setNextFocusDownId(firstCard.getId());}offlineRow.post(()->loadVisibleAvatars(offlineRow));if(offline.isEmpty())offlineRow.addView(text(loadingDirectory?getString(R.string.ui_012):getString(R.string.ui_013),12,MUTED));
    }
    void renderRail(){HomeCatalog.sortLive(streams);StringBuilder sig=new StringBuilder();for(TwitchAccount.Stream item:streams)sig.append(item.login).append(item.id).append(item.avatar).append('|');if(sig.toString().equals(railSignature))return;railSignature=sig.toString();railOrder.clear();for(TwitchAccount.Stream item:streams)railOrder.add(item.login);String focused=rail.findFocus()==null?"":String.valueOf(rail.findFocus().getTag());rail.removeAllViews();
        for(TwitchAccount.Stream item:streams){LinearLayout cell=col();cell.setId(View.generateViewId());cell.setTag(item.id);cell.setGravity(Gravity.CENTER);cell.setFocusable(true);cell.setClickable(true);cell.setNextFocusRightId(watch.getId());cell.setClipChildren(false);cell.setClipToPadding(false);cell.setPadding(0,dp(5),0,dp(3));cell.setContentDescription(item.name+", "+(ChannelKey.kick(item.login)?"Kick":"Twitch"));FrameLayout avatar=new FrameLayout(this);avatar.setPadding(dp(2),dp(2),dp(2),dp(2));avatar.setBackground(outline(PANEL,28,ChannelKey.color(item.login)));avatarInto(avatar,item,50);avatar.setAlpha(.72f);cell.addView(avatar,new LinearLayout.LayoutParams(dp(50),dp(50)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(68),dp(68));lp.bottomMargin=dp(7);rail.addView(cell,lp);
            cell.setOnFocusChangeListener((v,f)->{avatar.setScaleX(f?1.3f:1);avatar.setScaleY(f?1.3f:1);avatar.setAlpha(f?1f:.72f);if(f){selected=item;watch.setNextFocusLeftId(cell.getId());renderSelection();}});cell.setOnClickListener(v->openChannel(item.login));if(selected!=null&&selected.id.equals(item.id))watch.setNextFocusLeftId(cell.getId());if(item.id.equals(focused))cell.requestFocus();
        }
    }
    /** Twitch is the slow platform: its lists start downloading with the intro and are used as soon as the Home asks for them. */
    void startPrefetch(){if(!account.connected())return;net.execute(()->{try{prefetchFollowed=account.followed();prefetchAt=SystemClock.elapsedRealtime();}catch(Exception ignored){}try{prefetchDirectory=account.allFollowed();}catch(Exception ignored){}});}
    synchronized List<TwitchAccount.Stream> takePrefetch(boolean followed){List<TwitchAccount.Stream> found=followed?prefetchFollowed:prefetchDirectory;if(found==null||SystemClock.elapsedRealtime()-prefetchAt>30000)return null;if(followed)prefetchFollowed=null;else prefetchDirectory=null;return new ArrayList<>(found);}
    void saveLiveCache(){try{org.json.JSONArray a=new org.json.JSONArray();for(TwitchAccount.Stream s:streams)if(!ChannelKey.kick(s.login))a.put(new JSONObject().put("id",s.id).put("login",s.login).put("name",s.name).put("title",s.title).put("game",s.game).put("thumb",s.thumbnail).put("avatar",s.avatar).put("viewers",s.viewers));prefs.edit().putString("live_cache_v1",a.toString()).putLong("live_cache_at",System.currentTimeMillis()).apply();}catch(Exception ignored){}}
    /** Shows the last known Twitch live list right away (if recent); the first refresh replaces it. */
    void loadLiveCache(){try{if(!account.connected()||System.currentTimeMillis()-prefs.getLong("live_cache_at",0)>1800000)return;org.json.JSONArray a=new org.json.JSONArray(prefs.getString("live_cache_v1","[]"));for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);TwitchAccount.Stream s=new TwitchAccount.Stream();s.id=j.optString("id");s.login=j.optString("login");s.name=j.optString("name",s.login);s.title=j.optString("title");s.game=j.optString("game");s.thumbnail=j.optString("thumb");s.avatar=j.optString("avatar");s.viewers=j.optInt("viewers");s.live=true;if(!s.login.isEmpty())streams.add(s);}}catch(Exception ignored){}}
    void loadCatalog(){try{org.json.JSONArray a=new org.json.JSONArray(prefs.getString("channel_catalog","[]"));for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);TwitchAccount.Stream item=new TwitchAccount.Stream();item.login=j.getString("key");if(ChannelKey.kick(item.login)?!KickSession.connected(this):!account.connected())continue;item.id=j.optString("id");item.name=j.optString("name",ChannelKey.slug(item.login));item.avatar=j.optString("avatar");directory.put(item.login,item);}}catch(Exception ignored){}for(TwitchAccount.Stream item:KickSession.allCached(this))directory.put(item.login,item);}
    void saveCatalog(){try{org.json.JSONArray a=new org.json.JSONArray();for(TwitchAccount.Stream item:directory.values())a.put(new JSONObject().put("key",item.login).put("id",item.id).put("name",item.name).put("avatar",item.avatar));prefs.edit().putString("channel_catalog",a.toString()).apply();}catch(Exception ignored){}}
    void refreshDirectory(){if(loadingDirectory||!account.connected()||SystemClock.elapsedRealtime()-directoryUpdated<300000&&directoryUpdated>0)return;loadingDirectory=true;String owner=account.name();net.execute(()->{try{List<TwitchAccount.Stream> early=takePrefetch(false);List<TwitchAccount.Stream> all=early!=null?early:account.allFollowed();ui(()->{loadingDirectory=false;if(destroyed||!account.connected()||!owner.equals(account.name()))return;directoryUpdated=SystemClock.elapsedRealtime();directory.entrySet().removeIf(e->!ChannelKey.kick(e.getKey()));for(TwitchAccount.Stream item:all)directory.put(item.login,item);saveCatalog();renderShelves();});}catch(Exception e){ui(()->{loadingDirectory=false;if(!destroyed&&!playing&&!browsingVods)status.setText(getString(R.string.ui_101));});}});}
    void renderSelection(){previewAudio.setVisibility(selected==null?View.GONE:View.VISIBLE);if(selected==null){heroImage.setImageDrawable(null);heroImage.setTag("");stopPreview();if(account.connected()||KickSession.connected(this)){heroName.setText(getString(R.string.ui_014));heroTitle.setText(getString(R.string.ui_104));heroMeta.setText(getString(R.string.ui_015));watch.setText(getString(R.string.ui_016));}return;}heroName.setText(selected.name);heroName.setTextColor(ChannelKey.color(selected.login));heroTitle.setText(selected.title);heroMeta.setText((ChannelKey.kick(selected.login)?"Kick":"Twitch")+" · "+selected.game+"  ·  "+String.format(Locale.getDefault(),"%,d",selected.viewers)+getString(R.string.ui_017));watch.setText(getString(R.string.ui_009));if(!selected.thumbnail.equals(heroImage.getTag()))loadImage(heroImage,selected.thumbnail,true);startPreview();}
    List<String> recentChannels(){
        List<String> out=new ArrayList<>();String stored=prefs.getString("recent_channels",prefs.getString("last_channel",""));
        for(String c:stored.split(","))if(c.matches("(?:kick:[a-z0-9_][a-z0-9_-]{0,24}|[a-z0-9_]{1,25})")&&!out.contains(c)&&out.size()<12)out.add(c);return out;
    }
    void rememberChannel(String c){List<String> recent=recentChannels();recent.remove(c);recent.add(0,c);if(recent.size()>12)recent=recent.subList(0,12);prefs.edit().putString("last_channel",c).putString("recent_channels",android.text.TextUtils.join(",",recent)).apply();}
    void stopPreview(){previewVersion++;if(previewJob!=null)main.removeCallbacks(previewJob);previewLogin="";if(previewView!=null){previewView.animate().cancel();previewView.setPlayer(null);previewView.setVisibility(View.INVISIBLE);}if(preview!=null){preview.release();preview=null;}}
    void startPreview(){
        if(previewView==null&&!browsingVods&&!playing&&heroVisual!=null&&homeReady){previewView=(PlayerView)getLayoutInflater().inflate(tv.duox.app.R.layout.home_preview,heroVisual,false);previewView.setVisibility(View.INVISIBLE);heroVisual.addView(previewView);}
        if(startupActive||playing||!foreground||previewView==null)return;if(browsingVods?!profileLive:selected==null)return;String login=browsingVods?libraryChannel:selected.login;if(login.equals(previewLogin))return;
        stopPreview();previewLogin=login;int version=previewVersion;
        previewJob=()->images.execute(()->{try{String url=resolveStream(login);
            // The player is built off the main thread (it is the expensive part); it still talks to the main looper.
            DefaultTrackSelector selector=new DefaultTrackSelector(this);selector.setParameters(selector.buildUponParameters().setMaxVideoSize(854,480).setMaxVideoBitrate(1200000));
            final ExoPlayer built=new ExoPlayer.Builder(this).setTrackSelector(selector).setLooper(Looper.getMainLooper()).build();
            main.post(()->{
            if(destroyed||playing||!foreground||version!=previewVersion){built.release();return;}
            preview=built;preview.setVolume(previewMuted?0:1);previewView.setPlayer(preview);previewView.setAlpha(0f);previewView.setVisibility(View.VISIBLE);preview.addListener(new Player.Listener(){@Override public void onRenderedFirstFrame(){if(version==previewVersion&&previewView!=null)previewView.animate().alpha(1f).setDuration(180).start();}});
            preview.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){if(version==previewVersion)stopPreview();}});
            preview.setMediaItem(MediaItem.fromUri(url));preview.prepare();preview.play();
        });}catch(Exception ignored){main.post(()->{if(version==previewVersion)stopPreview();});}});
        main.postDelayed(previewJob,650);
    }
    void loadImage(ImageView view,String url){loadImage(view,url,false);}
    /** With keep=true the current picture stays on screen until the new one is ready, so refreshes do not flash. */
    void loadImage(ImageView view,String url,boolean keep){view.setTag(url);if(!keep)view.setImageDrawable(null);if(!url.startsWith("https://"))return;Bitmap found=cache.get(url);if(found!=null){view.setImageBitmap(found);return;}images.execute(()->{try(Response r=TwitchSource.HTTP.newCall(new Request.Builder().url(url).build()).execute()){if(!r.isSuccessful()||r.body().contentLength()>4000000)return;byte[] b=r.peekBody(4000001).bytes();if(b.length>4000000)return;Bitmap bitmap=BitmapFactory.decodeByteArray(b,0,b.length);if(bitmap==null)return;cache.put(url,bitmap);main.post(()->{if(!destroyed&&url.equals(view.getTag()))view.setImageBitmap(bitmap);});}catch(Exception ignored){}});}
    void refresh(){if(playing||browsingVods)return;refreshKick();refreshDirectory();if(refreshing||!account.connected()||followedAt>0&&SystemClock.elapsedRealtime()-followedAt<8000)return;refreshing=true;followedAt=SystemClock.elapsedRealtime();int v=screenVersion;if(streams.isEmpty())status.setText(getString(R.string.ui_018));net.execute(()->{try{List<TwitchAccount.Stream> early=takePrefetch(true);List<TwitchAccount.Stream> result=early!=null?early:account.followed();ui(()->{refreshing=false;if(destroyed||playing||v!=screenVersion)return;for(TwitchAccount.Stream item:streams)if(ChannelKey.kick(item.login))result.add(item);streams=result;saveLiveCache();String old=selected==null?"":selected.id;selected=streams.stream().filter(s->s.id.equals(old)).findFirst().orElse(streams.isEmpty()?null:streams.get(0));for(TwitchAccount.Stream item:streams)directory.put(item.login,item);saveCatalog();renderRail();renderShelves();renderSelection();status.setText(streams.isEmpty()?getString(R.string.ui_019):streams.size()+getString(R.string.ui_020));});}catch(Exception e){ui(()->{refreshing=false;if(!destroyed&&!playing&&v==screenVersion)status.setText(account.connected()?getString(R.string.ui_021):getString(R.string.ui_022));});}});}
    void search(){if(searchScreen==null)searchScreen=new SearchScreen(this);}
    void kickAccount(){
        if(!KickSession.connected(this)){startActivityForResult(new Intent(this,KickActivity.class),27);return;}
        compactSheet(getString(R.string.ui_029),new String[]{getString(R.string.ui_030),getString(R.string.ui_031)},i->{
            if(i==0)startActivityForResult(new Intent(this,KickActivity.class),27);
            else{getSharedPreferences("kick",0).edit().clear().apply();String cookies=android.webkit.CookieManager.getInstance().getCookie("https://kick.com");if(cookies!=null)for(String part:cookies.split(";")){String name=part.trim().split("=",2)[0];for(String domain:new String[]{"kick.com",".kick.com"})android.webkit.CookieManager.getInstance().setCookie("https://kick.com",name+"=; Max-Age=0; Path=/; Domain="+domain+"; Secure");}android.webkit.CookieManager.getInstance().flush();directory.entrySet().removeIf(e->ChannelKey.kick(e.getKey()));saveCatalog();streams.removeIf(item->ChannelKey.kick(item.login));if(selected!=null&&ChannelKey.kick(selected.login))selected=null;home();}
        });
    }
    void refreshKick(){
        if(startupActive&&!warming||kickLoader!=null||!KickSession.connected(this)||playing||browsingVods||!foreground)return;int version=screenVersion;
        KickSession loader=new KickSession(this);kickLoader=loader;
        loader.refresh((json,error)->{if(kickLoader!=loader)return;kickLoader=null;loader.close();if(destroyed||playing||version!=screenVersion)return;
            if(error!=null){status.setText(getString(R.string.ui_032));return;}
            try{KickSession.save(this,json);directory.entrySet().removeIf(e->ChannelKey.kick(e.getKey()));for(TwitchAccount.Stream item:KickSession.parseAll(json))directory.put(item.login,item);List<TwitchAccount.Stream> kick=KickSession.parse(json);String old=selected==null?"":selected.id;streams.removeIf(item->ChannelKey.kick(item.login));streams.addAll(kick);selected=streams.stream().filter(item->item.id.equals(old)).findFirst().orElse(streams.isEmpty()?null:streams.get(0));for(TwitchAccount.Stream item:streams)directory.put(item.login,item);saveCatalog();renderRail();renderShelves();renderSelection();status.setText(streams.size()+getString(R.string.ui_020));}catch(Exception e){status.setText(getString(R.string.ui_033));}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==28&&result==RESULT_OK&&data!=null&&searchScreen!=null){java.util.ArrayList<String> matches=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(matches!=null&&!matches.isEmpty()){searchScreen.setQuery(matches.get(0));}return;}if(request==27){directory.entrySet().removeIf(e->ChannelKey.kick(e.getKey()));for(TwitchAccount.Stream item:KickSession.allCached(this))directory.put(item.login,item);saveCatalog();streams.removeIf(item->ChannelKey.kick(item.login));streams.addAll(KickSession.cached(this));home();}}
    void accountDialog(){if(!account.connected()){beginAuth();return;}compactSheet("Twitch · @"+account.name(),new String[]{getString(R.string.ui_034),getString(R.string.ui_035)},i->{if(i==0){directoryUpdated=0;refresh();}else{authVersion++;net.execute(()->{account.logout();main.post(()->{streams.removeIf(item->!ChannelKey.kick(item.login));directory.entrySet().removeIf(e->!ChannelKey.kick(e.getKey()));directoryUpdated=0;saveCatalog();selected=null;home();});});}});}
    void beginAuth(){
        int a=++authVersion;
        // DuoX-styled panel: QR on the left, big code and short steps on the right.
        LinearLayout panel=col();panel.setPadding(dp(22),dp(18),dp(22),dp(18));GradientDrawable frame=shape(0xf20a1418,24);frame.setStroke(dp(1),0x33ffffff);panel.setBackground(frame);
        TextView heading=text(getString(R.string.auth_title),21,MINT);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(0,0,0,dp(12));panel.addView(heading);
        LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.CENTER_VERTICAL);panel.addView(body);
        ImageView qr=new ImageView(this);qr.setContentDescription(getString(R.string.ui_036));qr.setBackground(shape(Color.WHITE,10));qr.setPadding(dp(6),dp(6),dp(6),dp(6));qr.setVisibility(View.INVISIBLE);body.addView(qr,new LinearLayout.LayoutParams(dp(170),dp(170)));
        LinearLayout right=col();right.setPadding(dp(22),0,0,0);body.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        TextView code=text(getString(R.string.ui_037),14,MUTED);right.addView(code);
        TextView big=text("",30,WHITE);big.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);big.setLetterSpacing(.12f);big.setPadding(0,dp(6),0,dp(6));right.addView(big);
        TextView steps=text("",12,MUTED);right.addView(steps);
        Button cancel=subtle(getString(R.string.ui_026),()->authDialog.dismiss());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(120),dp(34));cp.topMargin=dp(14);cp.gravity=Gravity.RIGHT;panel.addView(cancel,cp);
        authDialog=new Dialog(this);authDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);authDialog.setContentView(panel);
        authDialog.setOnDismissListener(d->{if(a==authVersion)authVersion++;});authDialog.show();authDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));authDialog.getWindow().setDimAmount(.55f);authDialog.getWindow().setLayout(dp(640),-2);cancel.requestFocus();
        net.execute(()->{try{
            JSONObject j=account.device();String userCode=j.getString("user_code"),device=j.getString("device_code");
            String url="https://www.twitch.tv/activate?public=true&device-code="+android.net.Uri.encode(userCode);
            com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,600,600);
            int[] pixels=new int[600*600];for(int y=0;y<600;y++)for(int x=0;x<600;x++)pixels[y*600+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            Bitmap bitmap=Bitmap.createBitmap(pixels,600,600,Bitmap.Config.ARGB_8888);
            main.post(()->{if(destroyed||a!=authVersion)return;
                pollInterval=Math.max(5,j.optInt("interval",5));authDeadline=SystemClock.elapsedRealtime()+j.optLong("expires_in",1800)*1000;
                qr.setImageBitmap(bitmap);qr.setVisibility(View.VISIBLE);
                code.setText(getString(R.string.auth_scan));big.setText(userCode);steps.setText(getString(R.string.auth_steps));poll(a,device,code);
            });
        }catch(Exception e){main.post(()->{if(a==authVersion)code.setText(getString(R.string.ui_039));});}});
    }
    void poll(int a,String device,TextView code){main.postDelayed(()->{if(destroyed||a!=authVersion)return;if(SystemClock.elapsedRealtime()>authDeadline){code.setText(getString(R.string.ui_040));return;}net.execute(()->{try{JSONObject tokens=account.poll(device);if(a!=authVersion)return;account.accept(tokens);if(a!=authVersion){account.logout();return;}main.post(()->{if(a!=authVersion||destroyed)return;authVersion++;authDialog.dismiss();home();});}catch(TwitchAccount.ApiError e){main.post(()->{if(a!=authVersion)return;if(AuthPolicy.pending(e.code)){pollInterval=AuthPolicy.nextInterval(pollInterval,e.code);poll(a,device,code);}else code.setText(getString(R.string.ui_041));});}catch(Exception e){main.post(()->{if(a==authVersion)poll(a,device,code);});}});},pollInterval*1000L);}
    static String resolveStream(String key)throws Exception{return ChannelKey.kick(key)?KickSource.resolve(key):TwitchSource.resolve(key);}
    void openChannel(String input){String c;try{c=ChannelKey.parse(input);}catch(Exception e){error(e.getMessage());return;}int r=++requestVersion;if(status!=null&&!playing)status.setText(getString(R.string.ui_042)+c+"…");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);net.execute(()->{try{String url=resolveStream(c);main.post(()->{if(!destroyed&&r==requestVersion)play(url,c);});}catch(Exception e){main.post(()->{if(!destroyed&&r==requestVersion)error(getString(R.string.ui_043)+c+getString(R.string.ui_044));});}});}
    void openLibrary(String input){
        final String key;try{key=ChannelKey.parse(input);}catch(Exception e){error(e.getMessage());return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
        release();playing=false;browsingVods=true;vodMode=false;libraryChannel=key;int version=++screenVersion;requestVersion++;root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=col();body.setPadding(dp(34),dp(24),dp(34),dp(24));scroll.addView(body);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TwitchAccount.Stream info=streamInfo(key);FrameLayout avatar=new FrameLayout(this);avatarInto(avatar,info,48);header.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView name=text((info==null?ChannelKey.slug(key):info.name),25,ChannelKey.color(key));name.setPadding(dp(14),0,0,0);header.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));final Boolean[] knownLive={null};Button favoriteButton=subtle("",()->{});updateFavoriteButton(favoriteButton,key);favoriteButton.setOnClickListener(v->{toggleFavorite(key,knownLive[0]);updateFavoriteButton(favoriteButton,key);});LinearLayout.LayoutParams favoriteP=new LinearLayout.LayoutParams(dp(48),dp(36));favoriteP.rightMargin=dp(12);header.addView(favoriteButton,favoriteP);Button syncButton=subtle(getString(R.string.sync_vod),()->new SyncScreen(this,key));LinearLayout.LayoutParams syncP=new LinearLayout.LayoutParams(dp(110),dp(36));syncP.rightMargin=dp(12);header.addView(syncButton,syncP);Button back=subtle(getString(R.string.ui_046),this::home);header.addView(back,new LinearLayout.LayoutParams(dp(100),dp(36)));body.addView(header);
        LinearLayout liveCard=new LinearLayout(this);liveCard.setGravity(Gravity.CENTER_VERTICAL);liveCard.setPadding(0,dp(4),0,dp(4));liveCard.setBackgroundColor(Color.TRANSPARENT);LinearLayout.LayoutParams liveP=new LinearLayout.LayoutParams(-1,-2);liveP.setMargins(0,dp(16),0,dp(10));body.addView(liveCard,liveP);LinearLayout liveDetails=col();liveCard.addView(liveDetails,new LinearLayout.LayoutParams(0,-2,1));FrameLayout liveVisual=new FrameLayout(this);liveVisual.setBackground(shape(BG,12));liveVisual.setClipToOutline(true);liveVisual.setVisibility(View.GONE);LinearLayout.LayoutParams visualP=new LinearLayout.LayoutParams(dp(300),dp(169));visualP.leftMargin=dp(18);liveCard.addView(liveVisual,visualP);ImageView liveImage=new ImageView(this);liveImage.setScaleType(ImageView.ScaleType.CENTER_CROP);liveVisual.addView(liveImage,new FrameLayout.LayoutParams(-1,-1));previewView=(PlayerView)getLayoutInflater().inflate(R.layout.home_preview,liveVisual,false);previewView.setVisibility(View.INVISIBLE);liveVisual.addView(previewView,new FrameLayout.LayoutParams(-1,-1));TextView liveState=text(getString(R.string.profile_check),15,MUTED);liveDetails.addView(liveState);TextView liveTitle=text("",18,WHITE);liveTitle.setMaxLines(2);liveDetails.addView(liveTitle);Button liveButton=subtle(getString(R.string.ui_025),()->openChannel(key));liveButton.setVisibility(View.GONE);LinearLayout liveActions=new LinearLayout(this);liveDetails.addView(liveActions);liveActions.addView(liveButton,new LinearLayout.LayoutParams(dp(150),dp(40)));previewAudio=subtle("",()->{previewMuted=!previewMuted;if(preview!=null)preview.setVolume(previewMuted?0:1);updatePreviewAudio();});previewAudio.setVisibility(View.GONE);LinearLayout.LayoutParams soundP=new LinearLayout.LayoutParams(dp(42),dp(40));soundP.leftMargin=dp(8);liveActions.addView(previewAudio,soundP);updatePreviewAudio();liveButton.setNextFocusRightId(previewAudio.getId());previewAudio.setNextFocusLeftId(liveButton.getId());
        net.execute(()->{try{TwitchAccount.Stream fresh=ChannelKey.kick(key)?KickSource.profile(key):account.profile(ChannelKey.slug(key));main.post(()->{if(destroyed||version!=screenVersion||!browsingVods)return;knownLive[0]=fresh.live;if(isFavorite(key))favoriteStates.observe(key,fresh.live);profileCache.put(key,fresh);if(profileCache.size()>32)profileCache.remove(profileCache.keySet().iterator().next());if(directory.containsKey(key))directory.put(key,fresh);name.setText(fresh.name);avatar.removeAllViews();avatarInto(avatar,fresh,48);liveState.setText(fresh.live?"● LIVE":"OFFLINE");liveState.setTextColor(fresh.live?ChannelKey.color(key):MUTED);liveTitle.setText(fresh.live?fresh.title:getString(R.string.profile_offline));liveButton.setVisibility(fresh.live?View.VISIBLE:View.GONE);profileLive=fresh.live;liveVisual.setVisibility(fresh.live?View.VISIBLE:View.GONE);previewAudio.setVisibility(fresh.live?View.VISIBLE:View.GONE);if(fresh.live){if(!fresh.thumbnail.isEmpty())loadImage(liveImage,fresh.thumbnail);startPreview();}});}catch(Exception e){main.post(()->{if(!destroyed&&version==screenVersion){liveState.setText(getString(R.string.profile_unknown));liveTitle.setText( !ChannelKey.kick(key)&&!account.connected()?getString(R.string.profile_login):getString(R.string.profile_retry));}});}});
        resumeFallback=back;resumeSection=col();body.addView(resumeSection);renderResume(false);
        TextView vodHeading=text("VOD",18,MINT);vodHeading.setPadding(0,dp(12),0,0);body.addView(vodHeading);
        TextView note=text(getString(R.string.ui_047),12,MUTED);note.setPadding(0,dp(14),0,dp(18));body.addView(note);LinearLayout grid=col();body.addView(grid);Button more=subtle(getString(R.string.ui_048),()->{});more.setVisibility(View.GONE);body.addView(more,new LinearLayout.LayoutParams(dp(150),dp(38)));back.requestFocus();loadVideos(key,"",grid,note,more,version,true);
    }
    String displayVideoDate(String raw){try{java.time.LocalDate date=java.time.LocalDate.parse(raw.substring(0,10));return date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(getResources().getConfiguration().getLocales().get(0)));}catch(Exception e){return raw;}}
    void loadVideos(String key,String cursor,LinearLayout grid,TextView note,Button more,int version,boolean first){
        more.setEnabled(false);net.execute(()->{try{VodSource.Page page=ChannelKey.kick(key)?VodSource.kick(key):account.videos(ChannelKey.slug(key),cursor);main.post(()->{if(destroyed||!browsingVods||version!=screenVersion)return;note.setText(page.videos.isEmpty()&&first?getString(R.string.ui_049):getString(R.string.ui_050));
            LinearLayout row=null;View initial=null;for(int i=0;i<page.videos.size();i++){VodSource.Video video=page.videos.get(i);if(i%3==0){row=new LinearLayout(this);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}LinearLayout card=col();card.setPadding(dp(7),dp(7),dp(7),dp(10));card.setBackground(shape(PANEL,12));card.setFocusable(true);card.setClickable(true);ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(thumb,new LinearLayout.LayoutParams(-1,dp(126)));loadImage(thumb,video.thumbnail);TextView title=text(video.title,13,WHITE);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(5),dp(8),dp(5),0);card.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));String date=displayVideoDate(video.date);TextView meta=text(date+"  ·  "+video.duration,11,MUTED);meta.setPadding(dp(5),0,0,0);card.addView(meta);card.setContentDescription(video.title+", "+date);card.setOnFocusChangeListener((v,f)->card.setBackground(f?outline(0xff254239,12,MINT):shape(PANEL,12)));card.setOnClickListener(v->openVideo(key,video));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(210),1);cp.setMargins(0,0,dp(12),dp(12));row.addView(card,cp);if(initial==null)initial=card;}
            if(row!=null)for(int i=page.videos.size()%3;i>0&&i<3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            more.setEnabled(true);more.setVisibility(page.cursor.isEmpty()?View.GONE:View.VISIBLE);more.setOnClickListener(v->loadVideos(key,page.cursor,grid,note,more,version,false));
        });}catch(Exception e){main.post(()->{if(!destroyed&&browsingVods&&version==screenVersion){note.setText(getString(R.string.video_load_error));more.setText(getString(R.string.ui_090));more.setEnabled(true);more.setVisibility(View.VISIBLE);more.setOnClickListener(v->loadVideos(key,cursor,grid,note,more,version,first));}});}});
    }
    /** Keeps the synced-VOD session (which VODs, where, and the offsets between them) in the profile's Continue watching row. */
    void saveSyncProgress(){
        ExoPlayer lead=panes.get(0).engine;int state=lead.getPlaybackState();if(state!=Player.STATE_READY&&state!=Player.STATE_ENDED)return;
        StringBuilder id=new StringBuilder("sync|"),offsets=new StringBuilder(),starts=new StringBuilder();
        for(int i=0;i<syncKeys.size();i++){if(i>0){id.append(',');offsets.append(',');starts.append(',');}id.append(syncKeys.get(i)).append('~').append(syncIds.get(i));offsets.append(i<syncOffsets.length?syncOffsets[i]-syncOffsets[0]:0);starts.append(syncStarts!=null&&i<syncStarts.length?syncStarts[i]:0);}
        VodHistory.Entry entry=new VodHistory.Entry(syncKeys.get(0),id.toString(),syncTitle==null?"":syncTitle,syncThumb==null?"":syncThumb,starts.toString(),offsets.toString(),syncNames==null?"":syncNames,0,0);
        vodHistory.record(entry,lead.getCurrentPosition(),lead.getDuration(),state==Player.STATE_ENDED);writeVodHistory();lastVodSave=SystemClock.elapsedRealtime();
    }
    /** Re-opens a saved synced-VOD session where it was left, with the same offsets between the VODs. */
    void resumeSynced(VodHistory.Entry entry){
        List<String> keys=VodHistory.syncKeys(entry),ids=VodHistory.syncIds(entry);if(keys.size()<2||keys.size()!=ids.size()){error(getString(R.string.resume_unavailable));return;}
        String[] offsetText=entry.durationLabel.split(","),startText=entry.date.split(",");long[] offsets=new long[keys.size()],starts=new long[keys.size()];
        for(int i=0;i<keys.size();i++){try{offsets[i]=Long.parseLong(offsetText[i]);}catch(Exception ignored){}try{starts[i]=Long.parseLong(startText[i]);}catch(Exception ignored){}}
        int request=++requestVersion;showSearchOverlay(getString(R.string.sync_preparing));final java.util.concurrent.atomic.AtomicBoolean cancel=syncCancel;
        net.execute(()->{try{
            List<String> urls=new ArrayList<>();
            for(int i=0;i<keys.size();i++){String key=keys.get(i);String url;
                if(ChannelKey.kick(key)){VodSource.Video fresh=null;for(VodSource.Video candidate:VodSource.kick(key).videos)if(candidate.id.equals(ids.get(i))){fresh=candidate;break;}if(fresh==null)throw new java.io.IOException("VOD unavailable");url=fresh.source;}else url=TwitchSource.resolveVod(ids.get(i));
                urls.add(url);}
            main.post(()->{hideSearchOverlay();if(cancel.get()||destroyed||!foreground||request!=requestVersion)return;
                startMulti(keys,urls,true);syncKeys=keys;syncUrls=urls;syncStarts=starts;syncIds=ids;syncTitle=entry.title;syncThumb=entry.thumbnail;syncNames=entry.name;
                for(int i=0;i<keys.size()&&i<panes.size();i++){syncOffsets[i]=offsets[i];panes.get(i).engine.seekTo(Math.max(0,entry.position+offsets[i]));}});
        }catch(Exception e){main.post(()->{hideSearchOverlay();if(!destroyed&&request==requestVersion)error(getString(R.string.resume_unavailable));});}});
    }
    /** Holding OK on a Continue-watching card for 2 s opens a small tab beside it to forget that VOD; the VOD itself does not open. */
    void attachForgetHold(View card,VodHistory.Entry entry){
        final boolean[] held={false};final Runnable open=()->{held[0]=true;showForgetTab(card,entry);};
        card.setOnKeyListener((v,k,e)->{if(k!=KeyEvent.KEYCODE_DPAD_CENTER&&k!=KeyEvent.KEYCODE_ENTER&&k!=KeyEvent.KEYCODE_NUMPAD_ENTER)return false;
            if(e.getAction()==KeyEvent.ACTION_DOWN){if(e.getRepeatCount()==0){held[0]=false;main.postDelayed(open,2000);}return true;}
            if(e.getAction()==KeyEvent.ACTION_UP){main.removeCallbacks(open);if(!held[0])v.performClick();held[0]=false;return true;}
            return false;});
    }
    void showForgetTab(View card,VodHistory.Entry entry){
        
        LinearLayout box=col();box.setPadding(dp(6),dp(6),dp(6),dp(6));box.setBackground(outline(0xf2101d1f,14,0x6669ffb4));
        android.widget.PopupWindow pop=new android.widget.PopupWindow(box,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,true);pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));pop.setElevation(dp(8));
        TextView go=tabRow("▶  "+getString(R.string.resume_continue)+" · "+videoTime(entry.position),()->{pop.dismiss();resumeEntry(entry);});
        TextView drop=tabRow("✕  "+getString(R.string.resume_forget),()->{pop.dismiss();vodHistory.remove(entry.channel,entry.id);writeVodHistory();renderResume(true);});
        box.addView(go);box.addView(drop);
        box.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED);int[] at=new int[2];card.getLocationOnScreen(at);int screenW=getResources().getDisplayMetrics().widthPixels;
        int x=at[0]+card.getWidth()+dp(6);if(x+box.getMeasuredWidth()>screenW-dp(8))x=Math.max(dp(8),at[0]-box.getMeasuredWidth()-dp(6));
        pop.showAtLocation(card,Gravity.TOP|Gravity.LEFT,x,at[1]+card.getHeight()/2-box.getMeasuredHeight()/2);go.requestFocus();
    }
    TextView tabRow(String label,Runnable run){
        TextView row=text(label,12,WHITE);row.setPadding(dp(12),dp(7),dp(14),dp(7));row.setSingleLine();row.setFocusable(true);row.setClickable(true);row.setBackground(null);
        row.setOnFocusChangeListener((v,f)->{row.setBackground(f?shape(0x3369ffb4,10):null);row.setTextColor(f?MINT:WHITE);});row.setOnClickListener(v->run.run());
        // the release of the key that opened this tab must not count as a press on it
        row.setOnKeyListener((v,k,e)->(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_UP&&!v.isPressed());return row;
    }
    /** Plays a Continue-watching entry from where it was left (single VOD or synced session). */
    void resumeEntry(VodHistory.Entry entry){
        if(VodHistory.isSync(entry)){resumeSynced(entry);return;}
        VodSource.Video video=new VodSource.Video();video.id=entry.id;video.title=entry.title;video.thumbnail=entry.thumbnail;video.date=entry.date;video.duration=entry.durationLabel;playVideo(entry.channel,video,entry.position);
    }
    void writeVodHistory(){prefs.edit().putString("vod_history_v1",vodHistory.encode()).apply();}
    void saveVodProgress(){
        if(synced&&vodMode&&syncIds!=null&&syncKeys!=null&&panes.size()>1&&syncIds.size()==syncKeys.size()){saveSyncProgress();return;}
        if(activeVod==null||player==null||!vodMode)return;
        int state=player.getPlaybackState();if(state!=Player.STATE_READY&&state!=Player.STATE_ENDED)return;
        vodHistory.record(activeVod,player.getCurrentPosition(),player.getDuration(),state==Player.STATE_ENDED);writeVodHistory();lastVodSave=SystemClock.elapsedRealtime();
    }
    void renderResume(boolean focus){
        if(resumeSection==null)return;resumeSection.removeAllViews();List<VodHistory.Entry> entries=vodHistory.recent();entries.removeIf(entry->!VodHistory.involves(entry,libraryChannel));resumeSection.setVisibility(entries.isEmpty()?View.GONE:View.VISIBLE);if(entries.isEmpty()){if(focus&&resumeFallback!=null)resumeFallback.requestFocus();return;}
        TextView heading=text(getString(R.string.resume_heading),15,WHITE);heading.setPadding(0,dp(18),0,dp(9));resumeSection.addView(heading);LinearLayout row=new LinearLayout(this);resumeSection.addView(shelf(row),new LinearLayout.LayoutParams(-1,dp(174)));View first=null;
        for(VodHistory.Entry entry:entries){
            LinearLayout card=col();card.setPadding(dp(6),dp(6),dp(6),dp(8));card.setBackground(shape(0xff111d20,14));card.setFocusable(true);card.setClickable(true);card.setContentDescription(entry.name+", "+entry.title+", "+getString(R.string.resume_at,videoTime(entry.position)));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(PANEL,9));image.setClipToOutline(true);FrameLayout thumbBox=new FrameLayout(this);thumbBox.addView(image,new FrameLayout.LayoutParams(-1,-1));if(VodHistory.isSync(entry)){ImageView badge=new ImageView(this);badge.setImageResource(R.drawable.ic_sync);badge.setColorFilter(MINT);badge.setPadding(dp(4),dp(4),dp(4),dp(4));badge.setBackground(shape(0xcc0a141b,10));badge.setContentDescription(getString(R.string.sync_vod));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(5),dp(5),0,0);thumbBox.addView(badge,bp);}card.addView(thumbBox,new LinearLayout.LayoutParams(-1,dp(98)));loadImage(image,entry.thumbnail);
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.min(1000,entry.position*1000/Math.max(1,entry.duration)));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff2c3b3c));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(2));pp.topMargin=dp(5);card.addView(progress,pp);
            TextView title=text(entry.title,12,WHITE);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(3),dp(6),dp(3),0);card.addView(title);TextView detail=text(entry.name+" · "+videoTime(entry.position)+" / "+videoTime(entry.duration),10,MUTED);detail.setSingleLine();detail.setEllipsize(TextUtils.TruncateAt.END);detail.setPadding(dp(3),dp(2),dp(3),0);card.addView(detail);
            card.setOnFocusChangeListener((v,f)->card.setBackground(f?outline(0xff1b302c,14,MINT):shape(0xff111d20,14)));attachForgetHold(card,entry);card.setOnClickListener(v->{if(VodHistory.isSync(entry)){resumeSynced(entry);return;}VodSource.Video video=new VodSource.Video();video.id=entry.id;video.title=entry.title;video.thumbnail=entry.thumbnail;video.date=entry.date;video.duration=entry.durationLabel;offerResume(entry.channel,video,false);});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(212),dp(165));cp.rightMargin=dp(10);row.addView(card,cp);if(first==null)first=card;
        }if(focus&&first!=null)first.requestFocus();
    }
    void openVideo(String key,VodSource.Video video){offerResume(key,video,false);}
    void offerResume(String key,VodSource.Video video,boolean removable){
        VodHistory.Entry saved=vodHistory.get(key,video.id);if(saved==null){playVideo(key,video,0);return;}
        String[] options={getString(R.string.resume_at,videoTime(saved.position)),getString(R.string.resume_restart),getString(R.string.ui_026)};
        compactSheet(getString(R.string.resume_heading),options,i->{if(i==2)return;playVideo(key,video,i==0?saved.position:0);});
    }
    void playVideo(String key,VodSource.Video video,long position){int request=++requestVersion;net.execute(()->{try{
        // Refresh Kick's playback URL instead of persisting an expiring stream link.
        VodSource.Video resolved=video;if(ChannelKey.kick(key)&&video.source.isEmpty()){resolved=null;for(VodSource.Video candidate:VodSource.kick(key).videos)if(candidate.id.equals(video.id)){resolved=candidate;break;}if(resolved==null)throw new java.io.IOException("VOD unavailable");}
        VodSource.Video current=resolved;String url=ChannelKey.kick(key)?current.source:TwitchSource.resolveVod(current.id);main.post(()->{if(!destroyed&&foreground&&request==requestVersion){startMulti(Collections.singletonList(key),Collections.singletonList(url),true);String name=streamInfo(key)==null?ChannelKey.slug(key):streamInfo(key).name;activeVod=new VodHistory.Entry(key,current.id,current.title,current.thumbnail,current.date,current.duration,name,position,0);lastVodSave=SystemClock.elapsedRealtime();if(position==0){vodHistory.remove(key,current.id);writeVodHistory();}else player.seekTo(position);player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED)saveVodProgress();}});channelHeading.setText(name+" · VOD");}});
    }catch(Exception e){main.post(()->{if(!destroyed&&request==requestVersion)error(getString(R.string.resume_unavailable));});}});}
    void seekVod(long amount){if(player!=null&&player.isCurrentMediaItemSeekable()){long duration=player.getDuration();long target=Math.max(0,player.getCurrentPosition()+amount);if(duration!=C.TIME_UNSET)target=Math.min(target,duration);seekAll(target);}scheduleHide();}
    /** Seeks the audio pane to the target and every other pane to the same moment, using the sync offsets. */
    void seekAll(long activeTarget){long base=activeTarget-(activePane<syncOffsets.length?syncOffsets[activePane]:0);for(int i=0;i<panes.size();i++)panes.get(i).engine.seekTo(Math.max(0,base+(i<syncOffsets.length?syncOffsets[i]:0)));}
    void play(String url,String c){startMulti(Collections.singletonList(c),Collections.singletonList(url));}
    TwitchAccount.Stream streamInfo(String login){for(TwitchAccount.Stream item:streams)if(item.login.equals(login))return item;return directory.containsKey(login)?directory.get(login):profileCache.get(login);}
    void startMulti(List<String> logins,List<String> urls){startMulti(logins,urls,false);}
    void startMulti(List<String> logins,List<String> urls,boolean archived){
        release();browsingVods=false;vodMode=archived;playing=true;screenVersion++;paused=false;root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);getWindow().addFlags(128);
        videoGrid=col();root.addView(videoGrid,new FrameLayout.LayoutParams(-1,-1));
        int count=logins.size(),columns=count==1?1:2,rows=count>2?2:1;
        for(int rowIndex=0;rowIndex<rows;rowIndex++){
            LinearLayout row=new LinearLayout(this);videoGrid.addView(row,new LinearLayout.LayoutParams(-1,0,1));
            for(int column=0;column<columns;column++){
                int index=rowIndex*columns+column;if(index>=count){row.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));continue;}
                Pane pane=new Pane();pane.login=logins.get(index);pane.selector=new DefaultTrackSelector(this);
                boolean together=archived&&count>1;int capW=!together?Integer.MAX_VALUE:count==2?854:640,capH=!together?Integer.MAX_VALUE:count==2?480:360;
                // Live multiview: 2-4 decoders at full quality is what stalls the Fire TV after a long time; each pane only needs about its own share of the screen.
                if(!archived&&count>1){capW=count==2?1280:854;capH=count==2?720:480;}pane.capW=capW;pane.capH=capH;
                pane.selector.setParameters(pane.selector.buildUponParameters().setMaxVideoSize(capW,capH).setViewportSize(Integer.MAX_VALUE,Integer.MAX_VALUE,false));
                pane.engine=new ExoPlayer.Builder(this).setTrackSelector(pane.selector).setLoadControl(archived?new DefaultLoadControl():new DefaultLoadControl.Builder().setBufferDurationsMs(6000,20000,1500,3000).build()).build();pane.engine.setVolume(index==0?1:0);
                pane.box=new FrameLayout(this);pane.box.setPadding(dp(2),dp(2),dp(2),dp(2));row.addView(pane.box,new LinearLayout.LayoutParams(0,-1,1));
                pane.view=(PlayerView)getLayoutInflater().inflate(tv.duox.app.R.layout.home_preview,pane.box,false);pane.view.setPlayer(pane.engine);pane.box.addView(pane.view);
                pane.label=text(ChannelKey.label(pane.login),12,WHITE);pane.label.setPadding(dp(10),dp(5),dp(10),dp(5));pane.label.setBackgroundColor(0x80000000);pane.box.addView(pane.label,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
                panes.add(pane);pane.engine.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){if(!archived&&e.errorCode==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW){pane.engine.seekToDefaultPosition();pane.engine.prepare();return;}pane.label.setText(pane.login+getString(R.string.ui_052));}});
                pane.engine.setMediaItem(archived?MediaItem.fromUri(urls.get(index)):liveItem(urls.get(index)));pane.engine.prepare();pane.engine.play();rememberChannel(pane.login);if(!archived)latencyProbe(pane);
            }
        }
        synced=archived&&count>1;syncOffsets=new long[count];aligning=false;if(synced)main.postDelayed(syncTick,3000);
        controls=col();controls.setGravity(Gravity.CENTER_HORIZONTAL);controls.setPadding(dp(14),dp(3),dp(14),dp(6));controls.setBackground(shape(0x8c070e10,26));
        if(archived)addVodTimeline();
        LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setGravity(Gravity.CENTER_VERTICAL);controls.addView(row,new LinearLayout.LayoutParams(-2,dp(60)));
        LinearLayout avatarCell=col();avatarCell.setGravity(Gravity.CENTER_HORIZONTAL);channelHeading=text("",10,WHITE);channelHeading.setSingleLine();channelHeading.setEllipsize(TextUtils.TruncateAt.END);channelHeading.setGravity(Gravity.CENTER);channelHeading.setVisibility(View.INVISIBLE);LinearLayout.LayoutParams headingP=new LinearLayout.LayoutParams(dp(80),dp(14));headingP.bottomMargin=dp(5);avatarCell.addView(channelHeading,headingP);
        channelIcon=new ImageView(this);channelIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);channelIcon.setBackgroundColor(PANEL);channelIcon.setForeground(ring(0,MUTED,1));channelIcon.setOutlineProvider(new android.view.ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});channelIcon.setClipToOutline(true);channelIcon.setFocusable(true);channelIcon.setClickable(true);channelIcon.setContentDescription(getString(R.string.view_profile));channelIcon.setOnClickListener(v->openLibrary(channel));
        channelIcon.setOnFocusChangeListener((v,f)->{channelHeading.setVisibility(f?View.VISIBLE:View.INVISIBLE);channelIcon.setForeground(ring(0,f?MINT:ChannelKey.color(channel),f?2:1));scheduleHide();});avatarCell.addView(channelIcon,new LinearLayout.LayoutParams(dp(30),dp(30)));row.addView(avatarCell,new LinearLayout.LayoutParams(dp(80),-1));
        pause=tool(row,R.drawable.ic_pause,getString(R.string.ui_053),46,this::togglePause);
        if(archived){tool(row,R.drawable.ic_rewind,getString(R.string.seek_back),46,()->seekVod(-30000));tool(row,R.drawable.ic_forward,getString(R.string.seek_forward),46,()->seekVod(30000));audioTool(row,count);if(synced){tool(row,R.drawable.ic_offset,getString(R.string.sync_adjust),62,this::syncAdjust);tool(row,R.drawable.ic_sync,getString(R.string.sync_align),62,this::syncAlign);}tool(row,R.drawable.ic_quality,getString(R.string.ui_055),46,this::quality);tool(row,R.drawable.ic_videos,getString(R.string.ui_056),46,()->openLibrary(channel));}
        else{
            // Audio only exists with several streams; up/down switches the channel in place.
            audioTool(row,count);
            tool(row,R.drawable.ic_quality,getString(R.string.ui_055),46,this::quality);favoriteTool=tool(row,R.drawable.ic_star_border,getString(R.string.ui_058),46,this::favorite);tool(row,R.drawable.ic_chat,getString(R.string.chat_label),46,this::toggleChat);}
        tool(row,R.drawable.ic_home,getString(R.string.ui_059),46,this::home);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(archived?Math.min(synced?dp(730):dp(640),getResources().getDisplayMetrics().widthPixels-dp(48)):-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cp.setMargins(0,0,0,dp(20));root.addView(controls,cp);selectAudio(0);if(archived)main.post(vodTick);pause.requestFocus();scheduleHide();
        boolean entry=!archived&&!skipEntryPanel;skipEntryPanel=false;int shown=screenVersion;main.removeCallbacks(liveTick);if(!archived)main.postDelayed(liveTick,25000);
        if(entry)main.postDelayed(()->{if(!destroyed&&playing&&!vodMode&&shown==screenVersion&&livePanel==null&&controls!=null&&controls.getVisibility()==View.VISIBLE)openLivePanel(false);},450);
    }
    /** While watching, the list of who is live is refreshed quietly so the left panel is current whenever it opens. */
    final Runnable liveTick=new Runnable(){public void run(){if(destroyed||!playing||vodMode)return;if(foreground)refreshLiveBackground();main.postDelayed(this,45000);}};
    void refreshLiveBackground(){
        if(liveRefreshing)return;liveRefreshing=true;final List<TwitchAccount.Stream> snapshot=new ArrayList<>(streams);final boolean twitch=account.connected();
        net.execute(()->{List<TwitchAccount.Stream> next=new ArrayList<>();boolean ok=true;
            try{if(twitch)next.addAll(account.followed());else for(TwitchAccount.Stream item:snapshot)if(!ChannelKey.kick(item.login))next.add(item);}catch(Exception e){ok=false;}
            int checked=0;for(TwitchAccount.Stream item:snapshot){if(!ChannelKey.kick(item.login))continue;boolean live=true;if(checked++<16)try{live=KickSource.isLive(item.login);}catch(Exception ignored){}if(live)next.add(item);}
            final boolean good=ok;main.post(()->{liveRefreshing=false;if(destroyed||!playing||!good)return;streams=next;String old=selected==null?"":selected.id;selected=null;for(TwitchAccount.Stream item:next){directory.put(item.login,item);if(item.id.equals(old))selected=item;}saveLiveCache();});});
    }
    /** Audio needs several streams; up/down switches the channel in place. */
    void audioTool(LinearLayout row,int count){
        audioCaption=null;if(count<2)return;ImageView audio=tool(row,R.drawable.ic_audio,getString(R.string.audio_label),84,this::audioPicker);audioCaption=(TextView)audio.getTag();
        audio.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN||panes.size()<2)return false;if(k==KeyEvent.KEYCODE_DPAD_UP){selectAudio((activePane+panes.size()-1)%panes.size());return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN){selectAudio((activePane+1)%panes.size());return true;}return false;});
        // Same glass-circle bubble as the live panel's "+", clear of the caption line above the icon and drawn in front of the bar (never behind its panel/shadow): appears only while Audio has focus, hinting up/down switches the channel in place.
        ImageView bubble=new ImageView(this);bubble.setImageResource(R.drawable.ic_updown);bubble.setColorFilter(MINT);bubble.setPadding(dp(7),dp(7),dp(7),dp(7));
        GradientDrawable bf=shape(0x8010201f,20);bf.setStroke(dp(1),0x4469ffb4);bubble.setBackground(bf);bubble.setAlpha(0f);bubble.setElevation(dp(24));
        FrameLayout.LayoutParams bp2=new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP|Gravity.LEFT);root.addView(bubble,bp2);
        Runnable position=()->{if(bubble.getWidth()==0)return;int[] av=new int[2];audio.getLocationOnScreen(av);int[] rv=new int[2];root.getLocationOnScreen(rv);float ax=av[0]-rv[0],ay=av[1]-rv[1];bubble.setX(ax+audio.getWidth()/2f-bubble.getWidth()/2f);bubble.setY(ay-dp(19)-dp(8)-bubble.getHeight());};
        audio.setOnFocusChangeListener((v,f)->{if(f){bubble.post(position);bubble.animate().alpha(1f).setDuration(120).start();}else bubble.animate().alpha(0f).setDuration(120).start();scheduleHide();});
    }
    /** Icon button with a small label that appears above it while it has focus. */
    ImageView tool(LinearLayout row,int icon,String label,int width,Runnable run){
        LinearLayout cell=col();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setClipChildren(false);TextView caption=text(label,10,WHITE);caption.setSingleLine();caption.setEllipsize(TextUtils.TruncateAt.END);caption.setGravity(Gravity.CENTER);caption.setVisibility(View.INVISIBLE);LinearLayout.LayoutParams captionP=new LinearLayout.LayoutParams(dp(84),dp(14));captionP.gravity=Gravity.CENTER_HORIZONTAL;captionP.bottomMargin=dp(5);cell.addView(caption,captionP);
        ImageView button=new ImageView(this);button.setImageResource(icon);button.setColorFilter(WHITE);button.setPadding(dp(8),dp(8),dp(8),dp(8));button.setFocusable(true);button.setClickable(true);button.setContentDescription(label);button.setTag(caption);button.setOnClickListener(v->run.run());
        button.setOnFocusChangeListener((v,f)->{caption.setVisibility(f?View.VISIBLE:View.INVISIBLE);button.setColorFilter(f?MINT:WHITE);button.setBackground(f?shape(0x33ffffff,18):null);scheduleHide();});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(36),dp(36));cell.addView(button,bp);row.addView(cell,new LinearLayout.LayoutParams(dp(width),-1));return button;
    }
    void syncPause(){if(pause==null)return;pause.setImageResource(paused?R.drawable.ic_play:R.drawable.ic_pause);String label=getString(paused?R.string.ui_054:R.string.ui_053);pause.setContentDescription(label);if(pause.getTag() instanceof TextView)((TextView)pause.getTag()).setText(label);}
    void syncFavorite(){if(favoriteTool!=null)favoriteTool.setImageResource(isFavorite(channel)?R.drawable.ic_star:R.drawable.ic_star_border);}
    /** Diagnostic only: logs how far each live pane sits behind the live edge, for latency comparisons across devices. */
    MediaItem liveItem(String url){return new MediaItem.Builder().setUri(url).setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(6000).setMinOffsetMs(3000).setMaxOffsetMs(12000).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()).build();}
    /** Re-resolves the live URL (Kick and Twitch playback URLs carry a short-lived token, so reusing the old one ends in HTTP 403) and restarts the pane. */
    void reloadLive(Pane pane,String why){
        android.util.Log.w("DuoXStall",ChannelKey.label(pane.login)+" "+why+", resolving a fresh URL");
        net.execute(()->{try{String fresh=ChannelKey.kick(pane.login)?KickSource.resolve(pane.login):TwitchSource.resolve(pane.login);main.post(()->{if(destroyed||!panes.contains(pane))return;ExoPlayer e=pane.engine;e.stop();e.setMediaItem(liveItem(fresh));e.prepare();e.play();});}catch(Exception e){android.util.Log.w("DuoXStall","could not resolve a fresh URL: "+e);}});
    }
    /** Live watchdog. Seen on Fire TV: the stream stops advancing while ExoPlayer still reports READY/playing. A healthy live stream keeps a steady offset from the live edge; if the offset grows as fast as the clock for two checks, or the player is in error, reload the source. */
    void latencyProbe(Pane pane){
        long[] last={C.TIME_UNSET,0,0};int[] count={0,0,0};Runnable[] tick=new Runnable[1];
        tick[0]=()->{
            if(destroyed||!panes.contains(pane))return;
            ExoPlayer e=pane.engine;int state=e.getPlaybackState();long offset=e.getCurrentLiveOffset(),now=SystemClock.elapsedRealtime();
            boolean active=foreground&&!paused&&e.getPlayWhenReady();
            boolean frozen=active&&(state==Player.STATE_READY||state==Player.STATE_BUFFERING&&e.getTotalBufferedDuration()>4000)&&offset!=C.TIME_UNSET&&last[0]!=C.TIME_UNSET&&offset-last[0]>0.8*(now-last[1]);
            boolean failed=active&&e.getPlayerError()!=null;
            if((frozen||failed)&&now-last[2]>=8000&&count[1]<1000){
                if(failed||++count[0]>=1){count[1]++;count[0]=0;offset=C.TIME_UNSET;if(!failed&&now-last[2]>45000){last[2]=now-4000;android.util.Log.w("DuoXStall",ChannelKey.label(pane.login)+" stream stalled, jumping to the live edge");e.seekToDefaultPosition();}else{last[2]=now;reloadLive(pane,failed?"player error "+e.getPlayerError().getErrorCodeName():"still stalled");}}
            }else if(!frozen)count[0]=0;
            // Slow drift (the Fire TV playback clock running behind real time): past 20 s behind the live edge for 15 s, jump back to it.
            if(active&&state==Player.STATE_READY&&offset!=C.TIME_UNSET&&offset>20000){if(++count[2]>=3){count[2]=0;android.util.Log.w("DuoXStall",ChannelKey.label(pane.login)+" "+offset+"ms behind live, jumping to the live edge");e.seekToDefaultPosition();}}else count[2]=0;
            last[0]=offset;last[1]=now;
            android.util.Log.i("DuoXLatency",ChannelKey.label(pane.login)+" offsetMs="+offset+" bufferedMs="+e.getTotalBufferedDuration()+" state="+state+" error="+e.getPlayerError());
            main.postDelayed(tick[0],5000);
        };
        main.postDelayed(tick[0],5000);
    }
    static String videoTime(long ms){long sec=Math.max(0,ms/1000);return sec>=3600?String.format(Locale.US,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60):String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    void addVodTimeline(){
        vodTime=text("0:00 / —",12,MUTED);vodTime.setGravity(Gravity.CENTER);controls.addView(vodTime,new LinearLayout.LayoutParams(-1,dp(22)));
        vodSeek=new SeekBar(this);vodSeek.setId(View.generateViewId());vodSeek.setContentDescription(getString(R.string.ui_060));vodSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));vodSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(MINT));vodSeek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff40504f));controls.addView(vodSeek,new LinearLayout.LayoutParams(-1,dp(32)));
        vodSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){scrubbing=true;}public void onStopTrackingTouch(SeekBar bar){if(player!=null&&player.isCurrentMediaItemSeekable())seekAll(bar.getProgress()*1000L);scrubbing=false;scheduleHide();}public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(!fromUser||player==null)return;vodTime.setText(videoTime(progress*1000L)+" / "+videoTime(player.getDuration()));if(!scrubbing&&player.isCurrentMediaItemSeekable())seekAll(progress*1000L);scheduleHide();}});
        vodSeek.setOnFocusChangeListener((v,f)->{vodTime.setTextColor(f?MINT:MUTED);scheduleHide();});
    }
    void updateVodProgress(){if(player==null||vodSeek==null)return;long duration=player.getDuration();boolean available=duration>0&&duration!=C.TIME_UNSET&&player.isCurrentMediaItemSeekable();vodSeek.setEnabled(available);if(!available){vodTime.setText(videoTime(player.getCurrentPosition())+" / —");return;}vodSeek.setMax((int)Math.min(Integer.MAX_VALUE,duration/1000));vodSeek.setKeyProgressIncrement(10);if(!scrubbing){vodSeek.setProgress((int)(player.getCurrentPosition()/1000));vodSeek.setSecondaryProgress((int)(player.getBufferedPosition()/1000));vodTime.setText(videoTime(player.getCurrentPosition())+" / "+videoTime(duration));}}
    void selectAudio(int index){if(index<0||index>=panes.size())return;closeChat();activePane=index;Pane active=panes.get(index);channel=active.login;player=active.engine;tracks=active.selector;paused=!player.getPlayWhenReady();syncPause();
        for(int i=0;i<panes.size();i++){Pane item=panes.get(i);item.engine.setVolume(i==index?1:0);
            // Live multiview: only the pane with sound decodes audio (the muted ones used to keep a whole audio pipeline running for nothing).
            if(!vodMode&&panes.size()>1)item.selector.setParameters(item.selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,i!=index));
            item.box.setBackgroundColor(Color.BLACK);item.label.setText(ChannelKey.label(item.login));}syncLabels();
        main.removeCallbacks(fadeAudio);if(panes.size()>1){Drawable ring=new ColorDrawable(MINT);active.box.setBackground(ring);active.label.setText("● AUDIO · "+ChannelKey.label(active.login));main.postDelayed(fadeAudio,3000);}
        TwitchAccount.Stream info=streamInfo(channel);String shown=info==null?ChannelKey.slug(channel):info.name;channelHeading.setText(shown+" · "+(ChannelKey.kick(channel)?"Kick":"Twitch"));channelHeading.setTextColor(ChannelKey.color(channel));loadImage(channelIcon,info==null?"":info.avatar);channelIcon.setForeground(ring(0,ChannelKey.color(channel),1));if(audioCaption!=null)audioCaption.setText(shown);syncFavorite();
    }
    void audioPicker(){String[] names=new String[panes.size()];for(int i=0;i<names.length;i++)names[i]=getString(R.string.ui_061)+(i+1)+" · "+ChannelKey.label(panes.get(i).login);optionSheet(getString(R.string.ui_062),names,activePane,this::selectAudio);}
    void togglePause(){if(player==null)return;saveVodProgress();paused=!paused;for(Pane pane:panes)pane.engine.setPlayWhenReady(!paused);syncPause();scheduleHide();}
    void quality(){if(panes.size()==1){qualityFor(panes.get(0));return;}String[] names=new String[panes.size()];for(int i=0;i<names.length;i++)names[i]=getString(R.string.ui_061)+(i+1)+" · "+ChannelKey.label(panes.get(i).login);optionSheet(getString(R.string.ui_063),names,-1,w->qualityFor(panes.get(w)));}
    void qualityFor(Pane pane){
        List<TrackSelectionOverride> choices=new ArrayList<>();List<Format> formats=new ArrayList<>();
        for(Tracks.Group group:pane.engine.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<group.length;i++){Format f=group.getTrackFormat(i);if(group.isTrackSupported(i)&&f.height>=480){choices.add(new TrackSelectionOverride(group.getMediaTrackGroup(),i));formats.add(f);}}
        List<Integer> order=new ArrayList<>();for(int i=0;i<formats.size();i++)order.add(i);order.sort((x,y)->{int h=Integer.compare(formats.get(y).height,formats.get(x).height);if(h!=0)return h;return Float.compare(formats.get(y).frameRate,formats.get(x).frameRate);});
        List<String> labels=new ArrayList<>();labels.add("Auto");List<Integer> shown=new ArrayList<>();
        // One line per distinct quality (the top one is the maximum): no separate "highest available" duplicate.
        for(int i:order){Format f=formats.get(i);String label=f.height+"p"+(f.frameRate>0?" · "+Math.round(f.frameRate)+" fps":"");if(labels.contains(label))continue;labels.add(label);shown.add(i);}
        optionSheet(getString(R.string.ui_065)+ChannelKey.label(pane.login),labels.toArray(new String[0]),-1,w->{if(!panes.contains(pane))return;DefaultTrackSelector.Parameters.Builder params=pane.selector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(w>0?Integer.MAX_VALUE:pane.capW,w>0?Integer.MAX_VALUE:pane.capH).setMaxVideoBitrate(Integer.MAX_VALUE);if(w>0)params.setOverrideForType(choices.get(shown.get(w-1)));pane.selector.setParameters(params);if(pane.engine.getPlaybackState()==Player.STATE_IDLE){pane.engine.prepare();pane.engine.play();}});
    }
    void closeChat(){chatVersion++;if(chatQrBox!=null&&root!=null)root.removeView(chatQrBox);chatQrBox=null;if(chatText!=null&&root!=null)root.removeView(chatText);if(chat!=null){chat.close();chat=null;}if(kickChat!=null){kickChat.close();kickChat=null;}chatText=null;}
    void toggleChat(){if(chatText!=null){closeChat();return;}if(ChannelKey.kick(channel)){kickChat=new KickChat(this,ChannelKey.slug(channel));chatText=kickChat.view;}else{TwitchAccount.Stream info=streamInfo(channel);chat=new EmoteChat(this,channel,info==null?"":info.id);chatText=chat.view;}chatText.setBackground(shape(0x800c1519,14));chatText.setClipToOutline(true);if(ChannelKey.kick(channel))chatText.setAlpha(.9f);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(310),dp(350),Gravity.TOP|Gravity.RIGHT);cp.setMargins(0,dp(20),dp(20),0);root.addView(chatText,cp);chatQr(channel);}
    /** Small QR under the chat: opens the channel's public chat on a phone (Twitch popout chat, Kick channel page). No credentials are encoded. */
    void chatQr(String key){
        // Kick's popout chat redirects to a POST-only route after login, so Kick opens the regular channel page (chat included).
        String link=ChannelKey.kick(key)?"https://kick.com/"+ChannelKey.slug(key):"https://www.twitch.tv/popout/"+key+"/chat";
        try{com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,com.google.zxing.BarcodeFormat.QR_CODE,240,240,Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,1));int[] pixels=new int[240*240];for(int y=0;y<240;y++)for(int x=0;x<240;x++)pixels[y*240+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            LinearLayout box=col();box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(6),dp(6),dp(6),dp(4));box.setBackground(shape(0x99000000,10));ImageView code=new ImageView(this);code.setImageBitmap(Bitmap.createBitmap(pixels,240,240,Bitmap.Config.ARGB_8888));code.setContentDescription(getString(R.string.chat_qr_hint));box.addView(code,new LinearLayout.LayoutParams(dp(78),dp(78)));TextView hint=text(getString(R.string.chat_qr_hint),9,WHITE);hint.setGravity(Gravity.CENTER);box.addView(hint);
            FrameLayout.LayoutParams qp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);qp.setMargins(0,dp(20)+dp(350)+dp(8),dp(20),0);root.addView(box,qp);chatQrBox=box;
        }catch(Exception ignored){}
    }

    boolean isFavorite(String key){return prefs.getStringSet("favorites",Collections.emptySet()).contains(key);}
    void updateFavoriteButton(Button button,String key){boolean saved=isFavorite(key);button.setText(saved?"★":"☆");button.setTextSize(24);button.setContentDescription(getString(saved?R.string.favorite_remove:R.string.favorite_add));}
    void favorite(){toggleFavorite(channel,true);syncFavorite();}
    void toggleFavorite(String key,Boolean knownLive){Set<String> saved=new TreeSet<>(prefs.getStringSet("favorites",Collections.emptySet()));boolean removed=!saved.add(key);if(removed)saved.remove(key);prefs.edit().putStringSet("favorites",saved).apply();favoriteStates.retain(saved);if(!removed&&knownLive!=null)favoriteStates.observe(key,knownLive);Toast.makeText(this,removed?getString(R.string.ui_066):getString(R.string.ui_067),Toast.LENGTH_SHORT).show();}
    void error(String s){if(!destroyed)new AlertDialog.Builder(this).setTitle("DuoX TV").setMessage(s).setPositiveButton(getString(R.string.ui_068),null).show();}
    /** Light, translucent menu that sits just above the player bar. */
    Dialog softDialog(LinearLayout panel,int widthDp){
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);GradientDrawable frame=shape(0xb80a1418,22);frame.setStroke(dp(1),0x33ffffff);panel.setBackground(frame);dialog.setContentView(panel);dialog.setOnDismissListener(d->scheduleHide());dialog.show();
        Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(0f);w.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);WindowManager.LayoutParams lp=w.getAttributes();lp.y=dp(92);w.setAttributes(lp);w.setLayout(dp(widthDp),-2);return dialog;
    }
    TextView softTitle(String title){TextView heading=text(title.toUpperCase(Locale.ROOT),11,MUTED);heading.setLetterSpacing(.12f);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);heading.setPadding(dp(8),0,dp(8),dp(6));return heading;}
    /** A quiet list row: no box until it has focus. */
    Button softRow(String label,Runnable run){
        Button row=new Button(this);row.setId(View.generateViewId());row.setText(label);row.setTextSize(13);row.setAllCaps(false);row.setTextColor(WHITE);row.setSingleLine();row.setEllipsize(TextUtils.TruncateAt.END);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);row.setPadding(dp(12),0,dp(12),0);row.setMinHeight(0);row.setMinimumHeight(0);row.setBackground(null);row.setStateListAnimator(null);row.setOnClickListener(v->run.run());
        row.setOnFocusChangeListener((v,f)->{row.setBackground(f?shape(0x33ffffff,14):null);row.setTextColor(f?MINT:WHITE);});return row;
    }
    void optionSheet(String title,String[] choices,int selected,java.util.function.IntConsumer action){
        LinearLayout panel=col();panel.setPadding(dp(10),dp(12),dp(10),dp(10));panel.addView(softTitle(title));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=col();scroll.addView(list);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        final Dialog[] box=new Dialog[1];Button first=null;
        for(int i=0;i<choices.length;i++){final int index=i;Button row=softRow((i==selected?"✓  ":"")+choices[i],()->{box[0].dismiss();action.accept(index);});list.addView(row,new LinearLayout.LayoutParams(-1,dp(36)));if(first==null)first=row;}
        box[0]=softDialog(panel,264);if(choices.length>6)scroll.getLayoutParams().height=dp(6*36);if(first!=null)first.requestFocus();
    }
    // ---- Floating LIVE panel while watching --------------------------------------------------------------------------
    /** Same look as the Home rail but more transparent: jump to another live channel, or add it to the multiview with the + bubble. */
    void openLivePanel(){openLivePanel(true);}
    void openLivePanel(boolean focus){
        if(livePanel!=null||root==null||vodMode||panes.isEmpty())return;
        List<TwitchAccount.Stream> live=new ArrayList<>(streams);HomeCatalog.sortLive(live);
        if(live.isEmpty()){if(focus)Toast.makeText(this,getString(R.string.live_empty),Toast.LENGTH_SHORT).show();return;}
        final int frameColor=0x4469ffb4;
        FrameLayout wrap=new FrameLayout(this);wrap.setClipChildren(false);wrap.setClipToPadding(false);
        LinearLayout box=col();box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(8),dp(10),dp(8),dp(10));GradientDrawable frame=shape(0x8010201f,28);frame.setStroke(dp(1),frameColor);box.setBackground(frame);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout list=col();scroll.addView(list);box.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        final int row=dp(66),n=Math.max(1,Math.min(live.size(),((int)(getResources().getDisplayMetrics().heightPixels*.82f)-dp(20))/row));final int[] first={0};
        // Whole rows only, so no avatar is ever cut at the top or bottom edge; the panel scrolls a row at a time.
        wrap.addView(box,new FrameLayout.LayoutParams(dp(80),n*row+dp(20),Gravity.LEFT|Gravity.CENTER_VERTICAL));
        // The + bubble sits outside the panel, with the same glass style, next to the focused channel.
        ImageView bubble=new ImageView(this);bubble.setId(View.generateViewId());bubble.setImageResource(R.drawable.ic_add);bubble.setColorFilter(MINT);bubble.setPadding(dp(7),dp(7),dp(7),dp(7));
        // Reflects whether the focused channel is already in the multiview: + adds it (mint), - removes it (red).
        final Runnable[] syncBubble={null};
        GradientDrawable bubbleFrame=shape(0x8010201f,20);bubbleFrame.setStroke(dp(1),frameColor);bubble.setBackground(bubbleFrame);bubble.setAlpha(0f);bubble.setFocusable(true);bubble.setClickable(true);bubble.setContentDescription(getString(R.string.live_add));
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.LEFT|Gravity.TOP);bp.leftMargin=dp(88);wrap.addView(bubble,bp);
        List<LinearLayout> cells=new ArrayList<>();List<String> logins=new ArrayList<>();LinearLayout[] at={null};LinearLayout current=null;
        for(TwitchAccount.Stream item:live){
            LinearLayout cell=col();cell.setId(View.generateViewId());cell.setGravity(Gravity.CENTER);cell.setFocusable(true);cell.setClickable(true);cell.setContentDescription(item.name+", "+(ChannelKey.kick(item.login)?"Kick":"Twitch"));
            FrameLayout avatar=new FrameLayout(this);avatar.setPadding(dp(2),dp(2),dp(2),dp(2));avatar.setBackground(outline(PANEL,28,ChannelKey.color(item.login)));avatarInto(avatar,item,50);avatar.setAlpha(.72f);cell.addView(avatar,new LinearLayout.LayoutParams(dp(50),dp(50)));
            list.addView(cell,new LinearLayout.LayoutParams(dp(64),dp(66)));
            cell.setOnFocusChangeListener((v,f)->{avatar.setScaleX(f?1.3f:1);avatar.setScaleY(f?1.3f:1);avatar.setAlpha(f?1f:.72f);if(f){at[0]=cell;int idx=cells.indexOf(cell);if(idx<first[0])first[0]=idx;else if(idx>=first[0]+n)first[0]=idx-n+1;final int top=first[0];scroll.post(()->{scroll.scrollTo(0,top*row);bubble.animate().translationY(box.getTop()+dp(10)+(cells.indexOf(cell)-top)*row+(row-dp(32))/2f).alpha(1f).setDuration(120).start();});if(syncBubble[0]!=null)syncBubble[0].run();}armLivePanelHide();});
            cell.setNextFocusRightId(bubble.getId());cell.setNextFocusLeftId(cell.getId());
            cell.setOnClickListener(v->{closeLivePanel();if(panes.size()==1&&panes.get(0).login.equals(item.login))return;openChannel(item.login);});
            cells.add(cell);logins.add(item.login);if(item.login.equals(channel))current=cell;
        }
        for(int i=0;i<cells.size();i++){cells.get(i).setNextFocusUpId(cells.get(Math.max(0,i-1)).getId());cells.get(i).setNextFocusDownId(cells.get(Math.min(cells.size()-1,i+1)).getId());}
        bubble.setOnFocusChangeListener((v,f)->{bubble.setScaleX(f?1.1f:1);bubble.setScaleY(f?1.1f:1);boolean inMulti=inMulti(cells.indexOf(at[0])>=0?logins.get(cells.indexOf(at[0])):"");int strokeColor=inMulti?0xffff5c5c:frameColor;GradientDrawable g=shape(0x8010201f,20);g.setStroke(dp(f?2:1),f?(inMulti?0xffff5c5c:MINT):strokeColor);bubble.setBackground(g);armLivePanelHide();});
        syncBubble[0]=()->{int i=cells.indexOf(at[0]);boolean inMulti=i>=0&&inMulti(logins.get(i));bubble.setImageResource(inMulti?R.drawable.ic_remove:R.drawable.ic_add);bubble.setColorFilter(inMulti?0xffff5c5c:MINT);GradientDrawable g=shape(0x8010201f,20);g.setStroke(dp(bubble.isFocused()?2:1),inMulti?0xffff5c5c:(bubble.isFocused()?MINT:frameColor));bubble.setBackground(g);bubble.setContentDescription(getString(inMulti?R.string.live_remove:R.string.live_add));};
        bubble.setOnKeyListener((v,k,ev)->{if(ev.getAction()!=KeyEvent.ACTION_DOWN)return false;int i=Math.max(0,cells.indexOf(at[0]));if(k==KeyEvent.KEYCODE_DPAD_LEFT){cells.get(i).requestFocus();return true;}if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){cells.get(Math.max(0,Math.min(cells.size()-1,i+(k==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return k==KeyEvent.KEYCODE_DPAD_RIGHT;});
        bubble.setOnClickListener(v->{int i=cells.indexOf(at[0]);if(i<0)return;String login=logins.get(i);if(inMulti(login))removeFromMulti(login);else addToMulti(login);syncBubble[0].run();});
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(132),-1,Gravity.LEFT);lp.leftMargin=dp(16);
        livePanel=wrap;wrap.setAlpha(0f);root.addView(wrap,lp);wrap.animate().alpha(1f).setDuration(140).start();
        if(syncBubble[0]!=null)syncBubble[0].run();final LinearLayout start=current!=null?current:cells.get(0);livePanelEnter=()->{for(LinearLayout c:cells)c.setFocusable(true);bubble.setFocusable(true);start.requestFocus();};if(focus)start.requestFocus();else{for(LinearLayout c:cells)c.setFocusable(false);bubble.setFocusable(false);first[0]=Math.max(0,Math.min(cells.indexOf(start),cells.size()-n));scroll.post(()->scroll.scrollTo(0,first[0]*row));}armLivePanelHide();syncLabels();
    }
    void armLivePanelHide(){main.removeCallbacks(livePanelHide);if(livePanel!=null)main.postDelayed(livePanelHide,5000);}
    void closeLivePanel(){main.removeCallbacks(livePanelHide);boolean had=livePanel!=null&&livePanel.findFocus()!=null;if(livePanel!=null&&root!=null)root.removeView(livePanel);livePanel=null;livePanelEnter=null;if(had&&pause!=null)pause.requestFocus();syncLabels();scheduleHide();}
    boolean inMulti(String login){for(Pane pane:panes)if(pane.login.equals(login))return true;return false;}
    /** Adds a live channel to the streams already playing (max 4) by restarting the multiview with fresh URLs. */
    void addToMulti(String login){
        if(panes.size()>=4){Toast.makeText(this,getString(R.string.ui_074),Toast.LENGTH_SHORT).show();return;}
        if(inMulti(login))return;
        List<String> picked=new ArrayList<>();for(Pane pane:panes)picked.add(pane.login);picked.add(login);
        restartMultiWith(picked);
    }
    /** Drops a channel from the multiview, keeping the others playing (min 1 left). */
    void removeFromMulti(String login){
        if(panes.size()<=1||!inMulti(login))return;
        List<String> picked=new ArrayList<>();for(Pane pane:panes)if(!pane.login.equals(login))picked.add(pane.login);
        restartMultiWith(picked);
    }
    void restartMultiWith(List<String> picked){
        boolean keepPanel=livePanel!=null;int request=++requestVersion;
        net.execute(()->{try{List<String> urls=new ArrayList<>();for(String key:picked)urls.add(resolveStream(key));main.post(()->{if(!destroyed&&foreground&&request==requestVersion){skipEntryPanel=true;startMulti(picked,urls);if(keepPanel)openLivePanel(false);}});}catch(Exception e){main.post(()->{if(!destroyed&&request==requestVersion)error(getString(R.string.ui_076));});}});
    }
    // ---- Synced VOD -------------------------------------------------------------------------------------------------
    /** Keeps the panes together: small drift is corrected by nudging speed, large drift by seeking. */
    final Runnable syncTick=new Runnable(){public void run(){
        if(destroyed||!synced||panes.size()<2)return;
        if(!paused&&!aligning&&activePane<panes.size()){Pane ref=panes.get(activePane);if(ref.engine.getPlaybackState()==Player.STATE_READY)for(int i=0;i<panes.size();i++){if(i==activePane)continue;Pane other=panes.get(i);if(other.engine.getPlaybackState()!=Player.STATE_READY)continue;
            long expected=ref.engine.getCurrentPosition()+syncOffsets[i]-syncOffsets[activePane],diff=other.engine.getCurrentPosition()-expected;
            if(Math.abs(diff)>2500)other.engine.seekTo(Math.max(0,expected));else if(Math.abs(diff)>300)other.engine.setPlaybackSpeed(diff>0?.96f:1.04f);else if(other.engine.getPlaybackParameters().speed!=1f)other.engine.setPlaybackSpeed(1f);}}
        main.postDelayed(this,1500);}};
    static String signed(long ms){return String.format(Locale.US,"%s%.1f s",ms<0?"−":"+",Math.abs(ms)/1000.0);}
    void showSyncNotice(String message){if(root==null)return;if(syncNotice==null){syncNotice=text("",13,WHITE);syncNotice.setPadding(dp(14),dp(7),dp(14),dp(7));syncNotice.setBackground(shape(0x99070e10,18));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);p.topMargin=dp(18);root.addView(syncNotice,p);}syncNotice.setText(message);}
    void hideSyncNotice(){if(syncNotice!=null&&root!=null)root.removeView(syncNotice);syncNotice=null;}
    /** Manual offset per pane: left/right nudge it, OK changes the step (0.1 s, 0.5 s, 1 s, 5 s). */
    void syncAdjust(){
        if(!synced)return;LinearLayout panel=col();panel.setPadding(dp(10),dp(12),dp(10),dp(10));panel.addView(softTitle(getString(R.string.sync_offset_title)));
        TextView step=text("",12,MUTED);step.setPadding(dp(8),dp(6),0,0);final Dialog[] box=new Dialog[1];Button first=null;int[] steps={100,500,1000,5000};
        Runnable stepText=()->step.setText(getString(R.string.sync_offset_hint)+"   ·   "+String.format(Locale.US,"%.1f s",adjustStep/1000.0));
        for(int i=0;i<panes.size();i++){if(i==activePane)continue;final int index=i;Pane pane=panes.get(i);TwitchAccount.Stream info=streamInfo(pane.login);String name=info==null?ChannelKey.slug(pane.login):info.name;
            Button row=softRow("",()->{int at=0;for(int k=0;k<steps.length;k++)if(steps[k]==adjustStep)at=k;adjustStep=steps[(at+1)%steps.length];stepText.run();});
            Runnable label=()->row.setText(name+"    "+signed(syncOffsets[index]-syncOffsets[activePane]));label.run();
            row.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN||(k!=KeyEvent.KEYCODE_DPAD_LEFT&&k!=KeyEvent.KEYCODE_DPAD_RIGHT))return false;long delta=k==KeyEvent.KEYCODE_DPAD_LEFT?-adjustStep:adjustStep;syncOffsets[index]+=delta;pane.engine.seekTo(Math.max(0,pane.engine.getCurrentPosition()+delta));label.run();return true;});
            panel.addView(row,new LinearLayout.LayoutParams(-1,dp(38)));if(first==null)first=row;}
        stepText.run();panel.addView(step);box[0]=softDialog(panel,340);if(first!=null)first.requestFocus();
    }
    /** Full-screen "working" view: the DuoX logo with the spinning X while the search runs in the background. */
    void showSearchOverlay(String text){
        if(root==null)return;hideSearchOverlay();syncCancel=new java.util.concurrent.atomic.AtomicBoolean();
        FrameLayout overlay=new FrameLayout(this);overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff102c25,0xff0a141b}));overlay.setElevation(dp(60));overlay.setClickable(true);overlay.setFocusable(true);
        syncLogo=BrandMotionView.searching(this);overlay.addView(syncLogo,new FrameLayout.LayoutParams(dp(600),dp(260),Gravity.CENTER));syncLogo.running(true);
        syncOverlayText=text(text,15,MUTED);syncOverlayText.setGravity(Gravity.CENTER);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);tp.topMargin=dp(150);overlay.addView(syncOverlayText,tp);
        TextView hint=text(getString(R.string.sync_cancel_hint),12,MUTED);hint.setAlpha(.7f);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(28);overlay.addView(hint,hp);
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.requestFocus();syncOverlay=overlay;
    }
    void hideSearchOverlay(){if(syncLogo!=null)syncLogo.running(false);if(syncOverlay!=null){android.view.ViewParent parent=syncOverlay.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(syncOverlay);}syncOverlay=null;syncLogo=null;syncOverlayText=null;}
    void cancelSearch(){syncCancel.set(true);aligning=false;hideSearchOverlay();}
    /** Looks for the shared moment in the background (no playback needed) and updates the overlay text. */
    SyncSearch.Result searchShared(List<String> urls,long[] starts,long fromWallMs,java.util.concurrent.atomic.AtomicBoolean cancel){
        List<SyncSearch.Input> inputs=new ArrayList<>();for(int i=0;i<urls.size();i++)inputs.add(new SyncSearch.Input(urls.get(i),starts[i]));
        try{return SyncSearch.run(inputs,fromWallMs,text->{},cancel);}
        catch(Exception e){android.util.Log.w("DuoXSync","search failed: "+e);return null;}
    }
    /** In the player: find the shared moment from here on, jump there and set the offsets. */
    void syncAlign(){
        if(!synced||aligning||syncUrls==null||panes.size()<2)return;
        for(long start:syncStarts)if(start<=0){Toast.makeText(this,getString(R.string.sync_no_time),Toast.LENGTH_LONG).show();return;}
        aligning=true;showSearchOverlay(getString(R.string.sync_preparing));final java.util.concurrent.atomic.AtomicBoolean cancel=syncCancel;final long from=syncStarts[0]+panes.get(0).engine.getCurrentPosition();
        net.execute(()->{SyncSearch.Result r=searchShared(syncUrls,syncStarts,from,cancel);
            main.post(()->{aligning=false;if(cancel.get()||destroyed||!synced){hideSearchOverlay();return;}
                if(r==null||!r.found){hideSearchOverlay();showSyncNotice(getString(R.string.sync_no_match));main.postDelayed(this::hideSyncNotice,6000);return;}
                Runnable go=()->{hideSearchOverlay();long base=Math.max(0,r.masterPositionMs-20000);for(int i=0;i<panes.size()&&i<r.relativeMs.length;i++){syncOffsets[i]=r.relativeMs[i];panes.get(i).engine.seekTo(Math.max(0,base+r.relativeMs[i]));}showSyncNotice(getString(R.string.sync_found_at,SyncSearch.clock(base)));main.postDelayed(this::hideSyncNotice,5000);};
                if(syncLogo!=null)syncLogo.celebrate(go);else go.run();});});
    }
    /** Resolves the playback links, looks for where the VOD share voices and starts them together from there. */
    void startSyncedVods(List<SyncScreen.Slot> chosen){
        int request=++requestVersion;showSearchOverlay(getString(R.string.sync_preparing));final java.util.concurrent.atomic.AtomicBoolean cancel=syncCancel;
        net.execute(()->{try{
            List<String> keys=new ArrayList<>(),urls=new ArrayList<>();long[] starts=new long[chosen.size()];
            for(int i=0;i<chosen.size();i++){SyncScreen.Slot slot=chosen.get(i);VodSource.Video video=slot.video;starts[i]=VodSync.startMillis(video.date);
                String url;if(ChannelKey.kick(slot.key)){VodSource.Video fresh=null;for(VodSource.Video candidate:VodSource.kick(slot.key).videos)if(candidate.id.equals(video.id)){fresh=candidate;break;}if(fresh==null)throw new java.io.IOException("VOD unavailable");url=fresh.source;}else url=TwitchSource.resolveVod(video.id);
                keys.add(slot.key);urls.add(url);}
            final List<String> ids=new ArrayList<>(),names=new ArrayList<>();for(SyncScreen.Slot slot:chosen){ids.add(slot.video.id);names.add(slot.name.isEmpty()?ChannelKey.label(slot.key):slot.name);}final String title=chosen.get(0).video.title,thumb=chosen.get(0).video.thumbnail;
            boolean timed=true;for(long start:starts)if(start<=0)timed=false;
            SyncSearch.Result r=timed?searchShared(urls,starts,0,cancel):null;
            main.post(()->{if(cancel.get()||destroyed||!foreground||request!=requestVersion){hideSearchOverlay();return;}syncIds=ids;syncTitle=title;syncThumb=thumb;syncNames=android.text.TextUtils.join(" + ",names);beginSynced(keys,urls,starts,r);});
        }catch(Exception e){main.post(()->{hideSearchOverlay();if(!destroyed&&request==requestVersion)error(getString(R.string.resume_unavailable));});}});
    }
    void beginSynced(List<String> keys,List<String> urls,long[] starts,SyncSearch.Result r){
        final boolean found=r!=null&&r.found;long[] positions;
        if(found){positions=new long[keys.size()];long base=Math.max(0,r.masterPositionMs-20000);for(int i=0;i<positions.length;i++)positions[i]=Math.max(0,base+r.relativeMs[i]);}else positions=VodSync.startPositions(starts);
        final long[] pos=positions;
        Runnable go=()->{if(destroyed)return;hideSearchOverlay();startMulti(keys,urls,true);syncKeys=keys;syncUrls=urls;syncStarts=starts;
            for(int i=0;i<pos.length&&i<panes.size();i++){syncOffsets[i]=pos[i]-pos[0];panes.get(i).engine.seekTo(pos[i]);}
            Toast.makeText(this,found?getString(R.string.sync_found_at,SyncSearch.clock(pos[0])):getString(R.string.sync_not_found_start),Toast.LENGTH_LONG).show();};
        if(found&&syncLogo!=null)syncLogo.celebrate(go);else go.run();
    }
    /** Multiview names show only while the bottom bar or the left panel is in use. */
    void syncLabels(){boolean on=panes.size()>1&&(livePanel!=null||controls!=null&&controls.getVisibility()==View.VISIBLE);for(Pane item:panes)if(item.label!=null)item.label.setVisibility(on?View.VISIBLE:View.GONE);}
    void scheduleHide(){main.removeCallbacks(hide);if(playing&&controls!=null&&controls.getVisibility()==View.VISIBLE)main.postDelayed(hide,5000);}
    void release(){main.removeCallbacks(livePanelHide);livePanel=null;saveVodProgress();activeVod=null;resumeSection=null;resumeFallback=null;if(!keepIntro)closeIntro();clearNotices();main.removeCallbacks(vodTick);vodSeek=null;vodTime=null;scrubbing=false;if(kickLoader!=null){kickLoader.close();kickLoader=null;}stopPreview();profileLive=false;previewView=null;closeChat();main.removeCallbacks(hide);main.removeCallbacks(fadeAudio);main.removeCallbacks(syncTick);synced=false;aligning=false;syncNotice=null;held.clear();touching=false;for(Pane pane:panes){pane.view.setPlayer(null);pane.engine.release();}panes.clear();player=null;controls=null;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(syncOverlay!=null){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_BACK)cancelSearch();return true;}if(introCover!=null){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_BACK)finishIntro();return true;}if(playing&&livePanel!=null){if(e.getAction()==KeyEvent.ACTION_DOWN){armLivePanelHide();int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_LEFT&&livePanel.findFocus()==null&&livePanelEnter!=null){livePanelEnter.run();return true;}if(k==KeyEvent.KEYCODE_BACK||k==KeyEvent.KEYCODE_MENU){closeLivePanel();return true;}}return super.dispatchKeyEvent(e);}if(playing&&controls!=null){int k=e.getKeyCode();if(e.getAction()==KeyEvent.ACTION_UP){held.remove(k);scheduleHide();if(revealKey==k){revealKey=-1;return true;}}else{held.add(k);if(k==KeyEvent.KEYCODE_DPAD_LEFT&&!vodMode&&controls.getVisibility()==View.VISIBLE&&getCurrentFocus()==channelIcon){openLivePanel();return true;}if((k==KeyEvent.KEYCODE_MENU||k>=19&&k<=23)&&controls.getVisibility()!=View.VISIBLE){controls.setVisibility(View.VISIBLE);syncLabels();pause.requestFocus();revealKey=k;scheduleHide();return true;}scheduleHide();}}return super.dispatchKeyEvent(e);}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){touching=e.getActionMasked()!=MotionEvent.ACTION_UP&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL;scheduleHide();return super.dispatchTouchEvent(e);}
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)scheduleHide();else{held.clear();touching=false;main.removeCallbacks(hide);}}
    @Override public void onBackPressed(){if(playing){if(controls!=null&&controls.getVisibility()==View.VISIBLE){controls.setVisibility(View.GONE);syncLabels();main.removeCallbacks(hide);}else if(vodMode)openLibrary(channel);else home();}else if(browsingVods)home();else super.onBackPressed();}
    @Override protected void onStart(){super.onStart();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);foreground=true;main.removeCallbacks(favoriteTick);if(!startupActive)main.post(favoriteTick);refreshKick();if(!playing&&previewView!=null)startPreview();main.removeCallbacks(refreshTick);main.postDelayed(refreshTick,60000);if(playing&&vodMode){main.removeCallbacks(vodTick);main.post(vodTick);}}
    @Override protected void onStop(){if(syncOverlay!=null)cancelSearch();saveVodProgress();super.onStop();closeIntro();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);foreground=false;main.removeCallbacks(favoriteTick);clearNotices();main.removeCallbacks(vodTick);if(kickLoader!=null){kickLoader.close();kickLoader=null;}stopPreview();main.removeCallbacks(refreshTick);main.removeCallbacks(hide);authVersion++;requestVersion++;if(authDialog!=null)authDialog.dismiss();closeChat();if(player!=null){for(Pane pane:panes)pane.engine.pause();paused=true;syncPause();}}
    @Override protected void onDestroy(){if(updater!=null)updater.close();if(searchScreen!=null)searchScreen.dismiss();destroyed=true;authVersion++;requestVersion++;main.removeCallbacksAndMessages(null);release();net.shutdownNow();images.shutdownNow();cache.evictAll();super.onDestroy();}
    void cleanOldModels(){String[] names={"vosk-en-015","yamnet.tflite","jfk.wav","source.spm","target.spm","vocab.json","encoder_model_quantized_v2.ort","decoder_model_merged_quantized_v2.ort","encoder_model_quantized.onnx","decoder_model_merged_quantized.onnx","encoder_model_quantized.ort","decoder_model_merged_quantized.ort","ggml-tiny.en-q5_1.bin"};for(String n:names)delete(new java.io.File(getFilesDir(),n));java.io.File[] files=getCacheDir().listFiles((d,n)->n.startsWith("delay-"));if(files!=null)for(java.io.File f:files)delete(f);}
    void delete(java.io.File f){if(f.isDirectory()){java.io.File[] a=f.listFiles();if(a!=null)for(java.io.File x:a)delete(x);}f.delete();}
}
