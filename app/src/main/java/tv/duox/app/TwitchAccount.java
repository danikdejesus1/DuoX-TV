package tv.duox.app;
import android.content.*;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Official public-client Device Code grant. Never stores a password/client secret. */
final class TwitchAccount {
    static final String SCOPE="user:read:follows";
    private final OkHttpClient http=new OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build();
    private final TokenVault vault;
    private volatile JSONObject session;
    private long validatedAt;
    TwitchAccount(Context c){vault=new TokenVault(c);try{String s=vault.read();if(!s.isEmpty())session=new JSONObject(s);}catch(Exception e){vault.clear();}}
    String client(){return "pb290lsxxeug7wze8kzgvg833eypr7";}
    boolean connected(){return session!=null;}
    String name(){JSONObject current=session;return current==null?"":current.optString("login","");}
    synchronized void logout(){session=null;validatedAt=0;vault.clear();}
    synchronized void validateSession() throws Exception { if(session!=null)ensure(); }
    static final class ApiError extends IOException {final int status;final String code;ApiError(int s,String c){super("Twitch respondió "+s);status=s;code=c;}}
    private JSONObject request(Request r)throws Exception{try(Response res=http.newCall(r).execute()){JSONObject j=new JSONObject(res.body().string());if(!res.isSuccessful())throw new ApiError(res.code(),j.optString("message",j.optString("error","")));return j;}}
    private JSONObject post(String path,FormBody body)throws Exception{return request(new Request.Builder().url("https://id.twitch.tv/oauth2/"+path).post(body).build());}
    JSONObject device()throws Exception{return post("device",new FormBody.Builder().add("client_id",client()).add("scopes",SCOPE).build());}
    JSONObject poll(String code)throws Exception{return post("token",new FormBody.Builder().add("client_id",client()).add("scopes",SCOPE).add("device_code",code).add("grant_type","urn:ietf:params:oauth:grant-type:device_code").build());}
    synchronized void accept(JSONObject tokens)throws Exception {
        JSONObject validation=validate(tokens.getString("access_token"));check(validation);
        tokens.put("login",validation.optString("login")).put("user_id",validation.getString("user_id"));vault.write(tokens.toString());session=tokens;validatedAt=System.currentTimeMillis();
    }
    private JSONObject validate(String token)throws Exception{return request(new Request.Builder().url("https://id.twitch.tv/oauth2/validate").header("Authorization","OAuth "+token).build());}
    private void check(JSONObject j)throws Exception{boolean scope=false;JSONArray a=j.optJSONArray("scopes");if(a!=null)for(int i=0;i<a.length();i++)if(SCOPE.equals(a.getString(i)))scope=true;if(!client().equals(j.optString("client_id"))||!scope||j.optString("user_id").isEmpty())throw new IOException("Vuelve a conectar tu cuenta con permiso para ver tus seguidos");}
    private void refresh()throws Exception {
        if(session==null)throw new IOException("Conecta tu cuenta");
        try{JSONObject tokens=post("token",new FormBody.Builder().add("client_id",client()).add("grant_type","refresh_token").add("refresh_token",session.getString("refresh_token")).build());
            // Persist rotated refresh token before validation/network calls; it is single use.
            tokens.put("login",session.optString("login")).put("user_id",session.optString("user_id"));vault.write(tokens.toString());session=tokens;validatedAt=0;
        }catch(ApiError e){if(e.status==400||e.status==401)logout();throw e;}
    }
    private void ensure()throws Exception{
        if(session==null)throw new IOException("Conecta tu cuenta");
        if(System.currentTimeMillis()-validatedAt<3600000)return;
        try{JSONObject j=validate(session.getString("access_token"));check(j);if(j.optLong("expires_in")<90){refresh();j=validate(session.getString("access_token"));check(j);}validatedAt=System.currentTimeMillis();}
        catch(ApiError e){if(e.status!=401)throw e;refresh();JSONObject j=validate(session.getString("access_token"));check(j);validatedAt=System.currentTimeMillis();}
    }
    private JSONObject helix(HttpUrl url)throws Exception{
        try{return request(new Request.Builder().url(url).header("Client-Id",client()).header("Authorization","Bearer "+session.getString("access_token")).build());}
        catch(ApiError e){if(e.status!=401)throw e;refresh();ensure();return request(new Request.Builder().url(url).header("Client-Id",client()).header("Authorization","Bearer "+session.getString("access_token")).build());}
    }
    static final class Stream {String id="",login="",name="",title="",game="",thumbnail="",avatar="";int viewers;long followers;boolean live;}
    synchronized Stream profile(String login)throws Exception{
        ensure();JSONArray users=helix(HttpUrl.parse("https://api.twitch.tv/helix/users").newBuilder().addQueryParameter("login",login).build()).getJSONArray("data");if(users.length()==0)throw new IOException("Channel not found");JSONObject user=users.getJSONObject(0);Stream out=new Stream();out.login=login;out.id=user.getString("id");out.name=user.optString("display_name",login);out.avatar=user.optString("profile_image_url");JSONArray live=helix(HttpUrl.parse("https://api.twitch.tv/helix/streams").newBuilder().addQueryParameter("user_login",login).build()).getJSONArray("data");out.live=live.length()>0;if(out.live){JSONObject v=live.getJSONObject(0);out.title=v.optString("title");out.game=v.optString("game_name");out.viewers=v.optInt("viewer_count");out.thumbnail=v.optString("thumbnail_url").replace("{width}","640").replace("{height}","360");}return out;
    }
    synchronized Set<String> liveFavorites(List<String> names)throws Exception{
        ensure();Set<String> live=new HashSet<>();for(int i=0;i<names.size();i+=100){HttpUrl.Builder url=HttpUrl.parse("https://api.twitch.tv/helix/streams").newBuilder().addQueryParameter("first","100");for(String name:names.subList(i,Math.min(i+100,names.size())))url.addQueryParameter("user_login",name);JSONArray data=helix(url.build()).getJSONArray("data");for(int j=0;j<data.length();j++)live.add(data.getJSONObject(j).getString("user_login").toLowerCase(Locale.ROOT));}return live;
    }
    synchronized List<Stream> followed()throws Exception{
        ensure();List<Stream> out=new ArrayList<>();String cursor="";Set<String> seen=new HashSet<>();
        do{
            HttpUrl.Builder u=HttpUrl.parse("https://api.twitch.tv/helix/streams/followed").newBuilder().addQueryParameter("user_id",session.getString("user_id")).addQueryParameter("first","100");if(!cursor.isEmpty())u.addQueryParameter("after",cursor);
            JSONObject j=helix(u.build());JSONArray data=j.getJSONArray("data");
            for(int i=0;i<data.length();i++){JSONObject v=data.getJSONObject(i);Stream s=new Stream();s.id=v.getString("user_id");s.login=v.getString("user_login");s.name=v.getString("user_name");s.title=v.optString("title");s.game=v.optString("game_name");s.thumbnail=v.optString("thumbnail_url").replace("{width}","960").replace("{height}","540");s.viewers=v.optInt("viewer_count");out.add(s);}
            cursor=j.optJSONObject("pagination")==null?"":j.getJSONObject("pagination").optString("cursor","");
            if(!cursor.isEmpty()&&!seen.add(cursor))throw new IOException("Twitch repitió una página. Actualiza de nuevo");
        }while(!cursor.isEmpty());
        for(int start=0;start<out.size();start+=100){HttpUrl.Builder u=HttpUrl.parse("https://api.twitch.tv/helix/users").newBuilder();for(Stream s:out.subList(start,Math.min(start+100,out.size())))u.addQueryParameter("id",s.id);JSONArray users=helix(u.build()).getJSONArray("data");Map<String,String> icons=new HashMap<>();for(int i=0;i<users.length();i++){JSONObject v=users.getJSONObject(i);icons.put(v.getString("id"),v.optString("profile_image_url"));}for(Stream s:out.subList(start,Math.min(start+100,out.size())))s.avatar=icons.getOrDefault(s.id,"");}
        return out;
    }
    /** Public channel search (live and offline). Requires a connected account like the other Helix calls. */
    synchronized List<Stream> search(String query)throws Exception{
        ensure();List<Stream> out=new ArrayList<>();JSONArray data=helix(HttpUrl.parse("https://api.twitch.tv/helix/search/channels").newBuilder().addQueryParameter("query",query).addQueryParameter("first","60").build()).getJSONArray("data");
        for(int i=0;i<data.length();i++){JSONObject v=data.getJSONObject(i);Stream s=new Stream();s.id=v.optString("id");s.login=v.optString("broadcaster_login");s.name=v.optString("display_name",s.login);s.avatar=v.optString("thumbnail_url");s.live=v.optBoolean("is_live");s.title=v.optString("title");s.game=v.optString("game_name");if(!s.login.isEmpty())out.add(s);}
        return out;
    }
    synchronized List<Stream> allFollowed()throws Exception{
        ensure();List<Stream> out=new ArrayList<>();String cursor="";Set<String> seen=new HashSet<>(),ids=new HashSet<>();
        do{
            HttpUrl.Builder u=HttpUrl.parse("https://api.twitch.tv/helix/channels/followed").newBuilder().addQueryParameter("user_id",session.getString("user_id")).addQueryParameter("first","100");if(!cursor.isEmpty())u.addQueryParameter("after",cursor);
            JSONObject j=helix(u.build());JSONArray a=j.getJSONArray("data");
            for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);Stream s=new Stream();s.id=v.getString("broadcaster_id");if(!ids.add(s.id))continue;s.login=v.getString("broadcaster_login");s.name=v.getString("broadcaster_name");out.add(s);}
            cursor=j.optJSONObject("pagination")==null?"":j.getJSONObject("pagination").optString("cursor","");if(!cursor.isEmpty()&&!seen.add(cursor))throw new IOException("Página repetida");
        }while(!cursor.isEmpty());
        for(int start=0;start<out.size();start+=100){List<Stream> batch=out.subList(start,Math.min(start+100,out.size()));HttpUrl.Builder u=HttpUrl.parse("https://api.twitch.tv/helix/users").newBuilder();for(Stream s:batch)u.addQueryParameter("id",s.id);JSONArray users=helix(u.build()).getJSONArray("data");Map<String,String> icons=new HashMap<>();for(int i=0;i<users.length();i++){JSONObject v=users.getJSONObject(i);icons.put(v.getString("id"),v.optString("profile_image_url"));}for(Stream s:batch)s.avatar=icons.getOrDefault(s.id,"");}
        return out;
    }
    synchronized VodSource.Page videos(String login,String cursor)throws Exception{
        ensure();JSONArray users=helix(HttpUrl.parse("https://api.twitch.tv/helix/users").newBuilder().addQueryParameter("login",login).build()).getJSONArray("data");
        if(users.length()==0)throw new IOException("No se encontró el canal");
        HttpUrl.Builder u=HttpUrl.parse("https://api.twitch.tv/helix/videos").newBuilder().addQueryParameter("user_id",users.getJSONObject(0).getString("id")).addQueryParameter("type","archive").addQueryParameter("first","30");if(!cursor.isEmpty())u.addQueryParameter("after",cursor);
        JSONObject j=helix(u.build());VodSource.Page page=new VodSource.Page();JSONArray a=j.getJSONArray("data");
        for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);VodSource.Video video=new VodSource.Video();video.id=v.getString("id");video.title=v.optString("title");video.date=v.optString("created_at");video.duration=v.optString("duration");video.thumbnail=v.optString("thumbnail_url").replace("%{width}","480").replace("%{height}","270");page.videos.add(video);}
        if(j.optJSONObject("pagination")!=null)page.cursor=j.getJSONObject("pagination").optString("cursor","");return page;
    }

}
