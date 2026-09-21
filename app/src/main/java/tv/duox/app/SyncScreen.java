package tv.duox.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Picker for up to four VOD played together: pick a channel per slot, then one of its VOD (same-day ones are suggested first). */
@androidx.media3.common.util.UnstableApi
final class SyncScreen {
    static final int MAX=4;
    static final class Slot {String key="";String name="";VodSource.Video video;boolean filled(){return video!=null;}}
    private final MainActivity app;private final Dialog dialog;private final ExecutorService workers=Executors.newSingleThreadExecutor();
    private Button close;private final Slot[] slots=new Slot[MAX];private final LinearLayout slotBox,listBox;private final TextView note;private final Button start;private int version;

    SyncScreen(MainActivity app,String firstChannel){
        this.app=app;for(int i=0;i<MAX;i++)slots[i]=new Slot();dialog=new Dialog(app,android.R.style.Theme_Material_NoActionBar);
        LinearLayout page=new LinearLayout(app);page.setBackgroundColor(MainActivity.BG);page.setPadding(app.dp(40),app.dp(24),app.dp(36),app.dp(20));
        LinearLayout left=app.col();page.addView(left,new LinearLayout.LayoutParams(app.dp(400),-1));
        TextView title=app.text(app.getString(R.string.sync_title),26,MainActivity.MINT);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setLetterSpacing(.06f);title.setPadding(0,0,0,app.dp(14));left.addView(title);
        slotBox=app.col();left.addView(slotBox);
        LinearLayout actions=new LinearLayout(app);actions.setPadding(0,app.dp(14),0,0);left.addView(actions);
        start=app.subtle(app.getString(R.string.sync_start),this::begin);start.setTextColor(MainActivity.MINT);actions.addView(start,new LinearLayout.LayoutParams(app.dp(170),app.dp(40)));
        close=app.subtle(app.getString(R.string.ui_026),dialog::dismiss);start.setId(View.generateViewId());close.setId(View.generateViewId());start.setNextFocusRightId(close.getId());close.setNextFocusLeftId(start.getId());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(app.dp(110),app.dp(40));cp.leftMargin=app.dp(8);actions.addView(close,cp);
        LinearLayout right=app.col();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=app.dp(30);page.addView(right,rp);
        note=app.text("",12,MainActivity.MUTED);note.setPadding(0,app.dp(4),0,app.dp(10));right.addView(note);
        ScrollView scroll=new ScrollView(app);scroll.setVerticalScrollBarEnabled(false);listBox=app.col();scroll.addView(listBox);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(page);dialog.setOnDismissListener(d->{version++;workers.shutdownNow();});dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
        renderSlots();if(firstChannel!=null&&!firstChannel.isEmpty())chooseVod(0,firstChannel);else showChannels(0);
    }
    private int filled(){int n=0;for(Slot s:slots)if(s.filled())n++;return n;}
    private void renderSlots(){
        slotBox.removeAllViews();View first=null;List<View> rows=new ArrayList<>();
        for(int i=0;i<MAX;i++){
            final int index=i;Slot slot=slots[i];LinearLayout row=app.col();row.setPadding(app.dp(14),app.dp(8),app.dp(14),app.dp(8));row.setFocusable(true);row.setClickable(true);row.setBackground(app.shape(0x221f3534,12));
            TextView head=app.text(slot.filled()?(i+1)+"  "+slot.name+" · "+(ChannelKey.kick(slot.key)?"Kick":"Twitch"):(i+1)+"  +  "+app.getString(R.string.sync_add),14,slot.filled()?ChannelKey.color(slot.key):MainActivity.WHITE);head.setSingleLine();head.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(head);
            if(slot.filled()){TextView sub=app.text(slot.video.title+"  ·  "+stamp(slot.video.date)+"  ·  "+slot.video.duration,11,MainActivity.MUTED);sub.setSingleLine();sub.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(sub);}
            row.setOnFocusChangeListener((v,f)->row.setBackground(f?app.outline(0xff213b32,12,MainActivity.MINT):app.shape(0x221f3534,12)));row.setOnClickListener(v->{if(slot.filled()&&index>0&&false)return;showChannels(index);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,app.dp(56));lp.bottomMargin=app.dp(8);slotBox.addView(row,lp);rows.add(row);row.setId(View.generateViewId());if(first==null)first=row;
        }
        // "Empezar" stays reachable (dimmed) so the focus always lands on it going down; it only starts with 2 or more VODs.
        start.setEnabled(true);start.setAlpha(filled()>=2?1f:.45f);
        // Going down must land on "Empezar" (when it can start), never skip past it to "Cancelar".
        int below=start.getId();
        for(int i=0;i<rows.size();i++){rows.get(i).setNextFocusDownId(i<rows.size()-1?rows.get(i+1).getId():below);rows.get(i).setNextFocusUpId(i>0?rows.get(i-1).getId():rows.get(i).getId());}
        int last=rows.get(rows.size()-1).getId();start.setNextFocusUpId(last);close.setNextFocusUpId(last);start.setNextFocusDownId(start.getId());close.setNextFocusDownId(close.getId());
    }
    /** Right column: a short list of channels the person already follows, plus search. */
    private void showChannels(int slot){
        version++;listBox.removeAllViews();note.setText(app.getString(R.string.sync_pick_channel,slot+1));
        listBox.addView(row(app.getString(R.string.sync_search),"",null,()->new SearchScreen(app,key->chooseVod(slot,key),app.getString(R.string.sync_title)+"\n"+app.getString(R.string.sync_pick_screen,slot+1))));
        List<TwitchAccount.Stream> all=new ArrayList<>();Set<String> seen=new HashSet<>();for(TwitchAccount.Stream s:app.streams)if(seen.add(s.login))all.add(s);List<TwitchAccount.Stream> rest=new ArrayList<>(app.directory.values());rest.sort(Comparator.comparing(s->s.name.toLowerCase(Locale.ROOT)));for(TwitchAccount.Stream s:rest)if(seen.add(s.login))all.add(s);
        View first=listBox.getChildAt(0);
        for(TwitchAccount.Stream s:all){View r=row(s.name,(ChannelKey.kick(s.login)?"Kick":"Twitch"),s,()->chooseVod(slot,s.login));listBox.addView(r);}
        if(first!=null)first.requestFocus();
    }
    private View row(String title,String detail,TwitchAccount.Stream info,Runnable run){
        LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(10),app.dp(4),app.dp(12),app.dp(4));row.setFocusable(true);row.setClickable(true);row.setBackground(app.shape(Color.TRANSPARENT,12));
        if(info!=null){FrameLayout avatar=new FrameLayout(app);app.avatarInto(avatar,info,34);row.addView(avatar,new LinearLayout.LayoutParams(app.dp(34),app.dp(34)));}
        TextView name=app.text(title,15,MainActivity.WHITE);name.setPadding(app.dp(info==null?0:12),0,0,0);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(!detail.isEmpty()){TextView platform=app.text(detail,11,info==null?MainActivity.MUTED:ChannelKey.color(info.login));row.addView(platform);}
        row.setOnFocusChangeListener((v,f)->row.setBackground(f?app.outline(0xff213b32,12,MainActivity.MINT):app.shape(Color.TRANSPARENT,12)));row.setOnClickListener(v->run.run());
        row.setLayoutParams(new LinearLayout.LayoutParams(-1,app.dp(46)));return row;
    }
    private void chooseVod(int slot,String key){
        final int mine=++version;listBox.removeAllViews();note.setText(app.getString(R.string.sync_loading));
        workers.execute(()->{try{
            VodSource.Page page;if(ChannelKey.kick(key))page=VodSource.kick(key);else{if(!app.account.connected())throw new java.io.IOException("login");page=app.account.videos(ChannelKey.slug(key),"");}
            List<VodSource.Video> videos=page.videos;VodSource.Video anchor=slot>0?slots[0].video:null;List<VodSync.Ranked> ranked=anchor==null?null:VodSync.rank(anchor,videos);
            app.main.post(()->{if(mine!=version||!dialog.isShowing())return;listBox.removeAllViews();
                if(videos.isEmpty()){note.setText(app.getString(R.string.sync_no_vod));return;}
                note.setText(anchor==null?app.getString(R.string.sync_pick_vod):app.getString(R.string.sync_pick_similar,slots[0].name));
                View first=null;
                if(ranked!=null){for(VodSync.Ranked item:ranked){View r=vodRow(slot,key,item.video,item.sameDay,item.similarity);listBox.addView(r);if(first==null)first=r;}}
                else for(VodSource.Video video:videos){View r=vodRow(slot,key,video,false,0);listBox.addView(r);if(first==null)first=r;}
                if(first!=null)first.requestFocus();});
        }catch(Exception e){app.main.post(()->{if(mine==version&&dialog.isShowing())note.setText(app.getString("login".equals(e.getMessage())?R.string.profile_login:R.string.video_load_error));});}});
    }
    private View vodRow(int slot,String key,VodSource.Video video,boolean sameDay,double similarity){
        LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(8),app.dp(4),app.dp(12),app.dp(4));row.setFocusable(true);row.setClickable(true);row.setBackground(app.shape(Color.TRANSPARENT,12));
        ImageView thumb=new ImageView(app);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackground(app.shape(MainActivity.PANEL,6));thumb.setClipToOutline(true);row.addView(thumb,new LinearLayout.LayoutParams(app.dp(96),app.dp(54)));app.loadImage(thumb,video.thumbnail);
        LinearLayout text=app.col();text.setPadding(app.dp(12),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=app.text(video.title,14,MainActivity.WHITE);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);
        TextView meta=app.text(stamp(video.date)+"  ·  "+video.duration,11,MainActivity.MUTED);text.addView(meta);
        if(sameDay||similarity>=.25){TextView badge=app.text(sameDay?app.getString(R.string.sync_same_day):app.getString(R.string.sync_similar),11,MainActivity.MINT);badge.setPadding(app.dp(8),app.dp(2),app.dp(8),app.dp(2));badge.setBackground(app.outline(0x22000000,8,MainActivity.MINT));row.addView(badge);}
        row.setOnFocusChangeListener((v,f)->row.setBackground(f?app.outline(0xff213b32,12,MainActivity.MINT):app.shape(Color.TRANSPARENT,12)));
        row.setOnClickListener(v->{Slot s=slots[slot];s.key=key;TwitchAccount.Stream info=app.streamInfo(key);s.name=info==null?ChannelKey.slug(key):info.name;s.video=video;listBox.removeAllViews();note.setText(app.getString(R.string.sync_ready));renderSlots();if(filled()<MAX)showChannels(nextEmpty());else start.requestFocus();});
        row.setLayoutParams(new LinearLayout.LayoutParams(-1,app.dp(70)));return row;
    }
    private String stamp(String raw){long ms=VodSync.startMillis(raw);if(ms<=0)return app.displayVideoDate(raw);return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,app.getResources().getConfiguration().getLocales().get(0)).format(new Date(ms));}
    private int nextEmpty(){for(int i=0;i<MAX;i++)if(!slots[i].filled())return i;return MAX-1;}
    private void begin(){List<Slot> chosen=new ArrayList<>();for(Slot s:slots)if(s.filled())chosen.add(s);if(chosen.size()<2){android.widget.Toast.makeText(app,app.getString(R.string.sync_need_two),android.widget.Toast.LENGTH_SHORT).show();return;}dialog.dismiss();app.startSyncedVods(chosen);}
}
