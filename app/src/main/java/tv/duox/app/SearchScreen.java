package tv.duox.app;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Search: on-screen keyboard and field on the left, live-first channel suggestions on the right. */
@androidx.media3.common.util.UnstableApi
final class SearchScreen {
    private static final String KEYS="abcdefghijklmnopqrstuvwxyz0123456789_-.:/";
    private final MainActivity app;private final Dialog dialog;private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final StringBuilder query=new StringBuilder();private final EditText field;private final TextView note;private boolean updating;private final LinearLayout results;
    private final Map<String,TwitchAccount.Stream> found=new LinkedHashMap<>();
    private int version;private final Runnable[] pending=new Runnable[1];

    private final java.util.function.Consumer<String> pick;
    SearchScreen(MainActivity app){this(app,null);}
    /** With a pick callback the screen returns the chosen channel key instead of opening its profile. */
    SearchScreen(MainActivity app,java.util.function.Consumer<String> pick){this(app,pick,null);}
    /** With a pick callback the screen returns the chosen channel key instead of opening its profile; title names the step (synced VOD). */
    SearchScreen(MainActivity app,java.util.function.Consumer<String> pick,String title){
        this.app=app;this.pick=pick;dialog=new Dialog(app,android.R.style.Theme_Material_NoActionBar);
        LinearLayout page=new LinearLayout(app);boolean sub=false;if(sub){android.graphics.drawable.GradientDrawable frame=app.shape(0xf00a1418,24);frame.setStroke(app.dp(1),0x33ffffff);page.setBackground(frame);page.setPadding(app.dp(28),app.dp(20),app.dp(26),app.dp(18));}else{page.setBackgroundColor(MainActivity.BG);page.setPadding(app.dp(40),app.dp(22),app.dp(36),app.dp(20));}
        LinearLayout left=app.col();page.addView(left,new LinearLayout.LayoutParams(app.dp(330),-1));
        String[] lines=(title==null?app.getString(R.string.ui_024):title).split("\n",2);TextView heading=app.text(lines[0],19,MainActivity.MINT);heading.setTypeface(null,android.graphics.Typeface.BOLD);left.addView(heading);if(lines.length>1){TextView sub2=app.text(lines[1],12,MainActivity.MUTED);left.addView(sub2);}
        field=new EditText(app);field.setSingleLine();field.setTextSize(20);field.setTextColor(MainActivity.WHITE);field.setHintTextColor(MainActivity.MUTED);field.setGravity(Gravity.CENTER_VERTICAL);field.setPadding(app.dp(14),0,app.dp(14),0);field.setBackground(app.outline(0xff152225,12,0xff2c4a40));
        // The system keyboard (with Alexa voice input on Fire TV) only opens from the Voz button; the on-screen keys stay the main way to type.
        field.setShowSoftInputOnFocus(false);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);field.setFocusable(false);
        field.setOnEditorActionListener((v,action,e)->{hideKeyboard();open();return true;});
        field.setOnFocusChangeListener((v,focused)->{if(!focused){field.setFocusable(false);hideKeyboard();}});
        field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence c,int a,int b,int d){}public void onTextChanged(CharSequence c,int a,int b,int d){}public void afterTextChanged(android.text.Editable e){if(updating)return;query.setLength(0);query.append(e.toString().toLowerCase(Locale.ROOT));changed(false);}});
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,app.dp(44));fp.topMargin=app.dp(10);fp.bottomMargin=app.dp(8);left.addView(field,fp);
        // The three actions sit together right under the field, each with its icon; "Voz" is the smaller one in the middle.
        LinearLayout go2=new LinearLayout(app);
        Button goBtn=app.subtle("Ir",this::open);goBtn.setTextSize(15);goBtn.setTextColor(MainActivity.MINT);goBtn.setTypeface(null,android.graphics.Typeface.BOLD);goBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search,0,0,0);goBtn.setCompoundDrawablePadding(app.dp(6));goBtn.setPadding(app.dp(10),0,app.dp(10),0);
        LinearLayout.LayoutParams g1=new LinearLayout.LayoutParams(0,app.dp(44),1.05f);g1.rightMargin=app.dp(6);go2.addView(goBtn,g1);
        Button voiceBtn=app.subtle(app.getString(R.string.search_voice),this::voice);voiceBtn.setTextSize(13);voiceBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic,0,0,0);voiceBtn.setCompoundDrawablePadding(app.dp(4));voiceBtn.setPadding(app.dp(8),0,app.dp(8),0);
        LinearLayout.LayoutParams g2=new LinearLayout.LayoutParams(0,app.dp(44),.8f);g2.rightMargin=pick==null?app.dp(6):0;go2.addView(voiceBtn,g2);
        if(pick==null){Button sync=app.subtle(app.getString(R.string.sync_vod),()->{dialog.dismiss();new SyncScreen(app,"");});sync.setTextSize(14);sync.setTextColor(MainActivity.MINT);sync.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_sync,0,0,0);sync.setCompoundDrawablePadding(app.dp(6));sync.setPadding(app.dp(8),0,app.dp(8),0);sync.setBackground(app.outline(0x1f69ffb4,16,0x6669ffb4));sync.setOnFocusChangeListener((v,f)->sync.setBackground(app.outline(f?0x4469ffb4:0x1f69ffb4,16,f?MainActivity.MINT:0x6669ffb4)));go2.addView(sync,new LinearLayout.LayoutParams(0,app.dp(44),1.35f));}
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.bottomMargin=app.dp(8);left.addView(go2,gp);
        LinearLayout grid=app.col();left.addView(grid);LinearLayout row=null;Button first=null;
        for(int i=0;i<KEYS.length();i++){
            if(i%7==0){row=new LinearLayout(app);grid.addView(row,new LinearLayout.LayoutParams(-1,app.dp(38)));}
            final String key=String.valueOf(KEYS.charAt(i));Button button=app.subtle(key,()->type(key));button.setTextSize(15);button.setTextColor(MainActivity.WHITE);button.setOnFocusChangeListener((v,f)->{button.setBackground(f?app.outline(0xff234439,10,MainActivity.MINT):app.shape(0x221f3534,10));button.setTextColor(f?MainActivity.MINT:MainActivity.WHITE);});LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(app.dp(42),app.dp(35));kp.setMargins(0,0,app.dp(3),app.dp(3));row.addView(button,kp);if(first==null)first=button;
        }
        LinearLayout actions=new LinearLayout(app);left.addView(actions,new LinearLayout.LayoutParams(-1,app.dp(40)));
        actions.addView(app.subtle("⌫",this::backspace),lp(96));actions.addView(app.subtle("␣",()->type(" ")),lp(96));
        LinearLayout right=app.col();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=app.dp(28);page.addView(right,rp);
        note=app.text(app.getString(R.string.search_hint),13,MainActivity.MUTED);note.setPadding(0,app.dp(4),0,app.dp(10));right.addView(note);
        ScrollView scroll=new ScrollView(app);scroll.setVerticalScrollBarEnabled(false);results=app.col();scroll.addView(results);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(page);dialog.setOnDismissListener(d->close());dialog.show();dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));if(sub){dialog.getWindow().setDimAmount(.35f);dialog.getWindow().setLayout(app.dp(880),app.dp(560));}else{dialog.getWindow().setLayout(-1,-1);}dialog.getWindow().getDecorView().setSystemUiVisibility(5894);if(first!=null)first.requestFocus();
        showLocal("");
    }
    private LinearLayout.LayoutParams lp(int w){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(app.dp(w),app.dp(35));p.rightMargin=app.dp(4);return p;}
    void setQuery(String text){query.setLength(0);query.append(text.toLowerCase(Locale.ROOT));changed(true);}
    private void voice(){field.setFocusable(true);field.setFocusableInTouchMode(true);field.requestFocus();field.setSelection(field.length());((android.view.inputmethod.InputMethodManager)app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_FORCED);}
    private void hideKeyboard(){((android.view.inputmethod.InputMethodManager)app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);}
    private void type(String s){if(query.length()<40){query.append(s);changed(true);}}
    private void backspace(){if(query.length()>0){query.setLength(query.length()-1);changed(true);}}
    private void open(){hideKeyboard();String text=query.toString().trim();if(text.isEmpty())return;if(pick!=null){String key;try{key=ChannelKey.parse(text);}catch(Exception e){note.setText(app.getString(R.string.search_none));return;}dialog.dismiss();pick.accept(key);return;}dialog.dismiss();app.openLibrary(text);}
    private void changed(boolean fromKeys){
        if(fromKeys){updating=true;field.setText(query.toString());field.setSelection(field.length());updating=false;}version++;if(pending[0]!=null)app.main.removeCallbacks(pending[0]);found.clear();showLocal(query.toString().trim());
        String q=query.toString().trim();if(q.length()<2)return;final int mine=version;
        pending[0]=()->{note.setText(app.getString(R.string.search_loading));
            workers.execute(()->publish(mine,q,TwitchAccountSearch.run(app,q)));workers.execute(()->publish(mine,q,KickSearch.run(q)));};
        app.main.postDelayed(pending[0],350);
    }
    /** Results from an older query, or arriving after closing, are dropped. */
    private void publish(int mine,String q,List<TwitchAccount.Stream> items){app.main.post(()->{if(mine!=version||!dialog.isShowing())return;for(TwitchAccount.Stream item:items)found.merge(item.login,item,SearchScreen::keep);render(q);});}
    private void showLocal(String q){
        List<TwitchAccount.Stream> local=new ArrayList<>();for(TwitchAccount.Stream s:app.streams)local.add(copy(s,true));for(TwitchAccount.Stream s:app.directory.values())local.add(copy(s,false));
        if(!q.isEmpty()){Iterator<TwitchAccount.Stream> it=local.iterator();while(it.hasNext())if(SearchRank.relevance(it.next(),q)>2)it.remove();}
        for(TwitchAccount.Stream item:local)found.merge(item.login,item,SearchScreen::keep);render(q);
    }
    private static TwitchAccount.Stream keep(TwitchAccount.Stream a,TwitchAccount.Stream b){TwitchAccount.Stream best=b.live?b:a;best.followers=Math.max(a.followers,b.followers);return best;}
    private java.util.Set<String> followedKeys(){java.util.Set<String> keys=new java.util.HashSet<>();for(TwitchAccount.Stream s:app.streams)keys.add(s.login);keys.addAll(app.directory.keySet());return keys;}
    private static TwitchAccount.Stream copy(TwitchAccount.Stream s,boolean live){TwitchAccount.Stream c=new TwitchAccount.Stream();c.id=s.id;c.login=s.login;c.name=s.name;c.title=s.title;c.game=s.game;c.avatar=s.avatar;c.thumbnail=s.thumbnail;c.viewers=s.viewers;c.followers=s.followers;c.live=live||s.live;return c;}
    private void render(String q){
        List<TwitchAccount.Stream> ordered=SearchRank.order(found.values(),q.isEmpty()?"":q,followedKeys());if(ordered.size()>14)ordered=ordered.subList(0,14);
        results.removeAllViews();
        note.setText(q.isEmpty()?app.getString(R.string.search_hint):q.length()<2?app.getString(R.string.search_hint):ordered.isEmpty()?app.getString(R.string.search_none):app.getString(R.string.search_results));
        for(TwitchAccount.Stream item:ordered)results.addView(row(item),new LinearLayout.LayoutParams(-1,app.dp(60)));
    }
    private View row(TwitchAccount.Stream item){
        LinearLayout row=new LinearLayout(app);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(app.dp(10),app.dp(4),app.dp(12),app.dp(4));row.setFocusable(true);row.setClickable(true);row.setBackground(app.shape(Color.TRANSPARENT,12));
        FrameLayout avatar=new FrameLayout(app);app.avatarInto(avatar,item,42);row.addView(avatar,new LinearLayout.LayoutParams(app.dp(42),app.dp(42)));
        LinearLayout text=app.col();text.setPadding(app.dp(12),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=app.text(item.name,16,MainActivity.WHITE);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(name);
        String detail=item.live?"● "+app.getString(R.string.search_live)+(item.viewers>0?" · "+String.format(Locale.getDefault(),"%,d",item.viewers):"")+(item.game.isEmpty()?"":" · "+item.game):"OFFLINE";
        TextView sub=app.text(detail,12,item.live?ChannelKey.color(item.login):MainActivity.MUTED);sub.setSingleLine();sub.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(sub);
        TextView platform=app.text(ChannelKey.kick(item.login)?"Kick":"Twitch",11,ChannelKey.color(item.login));platform.setTypeface(null,android.graphics.Typeface.BOLD);platform.setPadding(app.dp(8),app.dp(2),app.dp(8),app.dp(2));platform.setBackground(app.outline(0x22000000,8,ChannelKey.color(item.login)));row.addView(platform);
        row.setContentDescription(item.name+", "+(ChannelKey.kick(item.login)?"Kick":"Twitch")+", "+(item.live?app.getString(R.string.search_live):"offline"));
        row.setOnFocusChangeListener((v,f)->row.setBackground(f?app.outline(0xff213b32,12,MainActivity.MINT):app.shape(Color.TRANSPARENT,12)));row.setOnClickListener(v->{dialog.dismiss();if(pick!=null)pick.accept(item.login);else app.openLibrary(item.login);});
        return row;
    }
    void dismiss(){dialog.dismiss();}
    void close(){version++;if(pending[0]!=null)app.main.removeCallbacks(pending[0]);workers.shutdownNow();if(app.searchScreen==this)app.searchScreen=null;}
    private static final class TwitchAccountSearch{static List<TwitchAccount.Stream> run(MainActivity app,String q){if(!app.account.connected())return Collections.emptyList();try{List<TwitchAccount.Stream> found=app.account.search(q);List<String> names=new ArrayList<>();for(TwitchAccount.Stream s:found)names.add(s.login);Map<String,Long> counts=TwitchSource.followers(names);for(TwitchAccount.Stream s:found)s.followers=counts.getOrDefault(s.login,0L);return found;}catch(Exception e){return Collections.emptyList();}}}
    private static final class KickSearch{static List<TwitchAccount.Stream> run(String q){try{return KickSource.search(q);}catch(Exception e){return Collections.emptyList();}}}
}
