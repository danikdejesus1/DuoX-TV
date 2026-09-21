package tv.duox.app;

import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

/** Experimental public HLS playback. No account cookies, proxies or challenge bypass. */
final class KickSource {
    static TwitchAccount.Stream profile(String key)throws Exception{
        HttpUrl endpoint=new HttpUrl.Builder().scheme("https").host("kick.com").addPathSegments("api/v2/channels").addPathSegment(ChannelKey.slug(key)).build();
        try(Response r=TwitchSource.HTTP.newCall(new Request.Builder().url(endpoint).header("Accept","application/json").build()).execute()){
            if(!r.isSuccessful()||r.body()==null)throw new IOException("Channel unavailable");byte[] bytes=r.peekBody(2000001).bytes();if(bytes.length>2000000)throw new IOException("Response too large");JSONObject data=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));TwitchAccount.Stream out=new TwitchAccount.Stream();out.login=key;out.id=key;out.name=ChannelKey.slug(key);JSONObject user=data.optJSONObject("user");if(user!=null){out.name=user.optString("username",out.name);out.avatar=user.optString("profile_pic");}JSONObject live=data.optJSONObject("livestream");out.live=live!=null;if(live!=null){out.title=live.optString("session_title");out.viewers=live.optInt("viewer_count");JSONObject thumb=live.optJSONObject("thumbnail");if(thumb!=null)out.thumbnail=thumb.optString("url",thumb.optString("src"));}return out;
        }
    }
    /** Public Kick channel search; the endpoint reports live state but not viewer counts. */
    static java.util.List<TwitchAccount.Stream> search(String query)throws Exception{
        HttpUrl endpoint=new HttpUrl.Builder().scheme("https").host("kick.com").addPathSegments("api/search").addQueryParameter("searched_word",query).build();
        try(Response r=TwitchSource.HTTP.newCall(new Request.Builder().url(endpoint).header("Accept","application/json").build()).execute()){
            if(!r.isSuccessful()||r.body()==null)throw new IOException("Kick search unavailable");byte[] bytes=r.peekBody(2000001).bytes();if(bytes.length>2000000)throw new IOException("Response too large");
            org.json.JSONArray channels=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).optJSONArray("channels");java.util.List<TwitchAccount.Stream> out=new java.util.ArrayList<>();if(channels==null)return out;
            for(int i=0;i<channels.length()&&out.size()<40;i++){JSONObject c=channels.optJSONObject(i);if(c==null)continue;String slug=c.optString("slug","").toLowerCase(java.util.Locale.ROOT);if(!slug.matches("[a-z0-9_][a-z0-9_-]{0,24}"))continue;
                TwitchAccount.Stream s=new TwitchAccount.Stream();s.login="kick:"+slug;s.id=s.login;s.name=slug;JSONObject user=c.optJSONObject("user");if(user!=null){if(!user.isNull("username"))s.name=user.optString("username",slug);if(!user.isNull("profilePic"))s.avatar=user.optString("profilePic");else if(!user.isNull("profile_pic"))s.avatar=user.optString("profile_pic");}s.live=c.optBoolean("isLive");s.followers=Math.max(c.optLong("followers_count",0),c.optLong("followersCount",0));out.add(s);}
            return out;
        }
    }
    static boolean isLive(String key)throws Exception{
        HttpUrl endpoint=new HttpUrl.Builder().scheme("https").host("kick.com").addPathSegments("api/v2/channels").addPathSegment(ChannelKey.slug(key)).build();
        try(Response r=TwitchSource.HTTP.newCall(new Request.Builder().url(endpoint).header("Accept","application/json").build()).execute()){
            if(!r.isSuccessful()||r.body()==null)throw new IOException("Kick status unavailable");byte[] bytes=r.peekBody(2000001).bytes();if(bytes.length>2000000)throw new IOException("Response too large");JSONObject data=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));return data.has("livestream")&&!data.isNull("livestream");
        }
    }
    static String resolve(String key)throws Exception{
        String normalized=ChannelKey.parse(key);
        if(!ChannelKey.kick(normalized))throw new IllegalArgumentException("Se esperaba un canal de Kick");
        HttpUrl endpoint=new HttpUrl.Builder().scheme("https").host("kick.com")
            .addPathSegments("api/v2/channels").addPathSegment(ChannelKey.slug(normalized)).build();
        try(Response r=TwitchSource.HTTP.newCall(new Request.Builder().url(endpoint).header("Accept","application/json").build()).execute()){
            if(!r.isSuccessful())throw new IOException("Kick no permitió abrir el directo ("+r.code()+")");
            if(r.body()==null||r.body().contentLength()>2000000)throw new IOException("Respuesta de Kick no válida");
            byte[] data=r.peekBody(2000001).bytes();if(data.length>2000000)throw new IOException("Respuesta de Kick demasiado grande");
            JSONObject j=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8));
            if(j.isNull("livestream")||!j.has("livestream"))throw new IOException("Este canal de Kick está desconectado");
            String url=j.optString("playback_url","");
            if(!validPlayback(url))throw new IOException("Kick no proporcionó una fuente de vídeo compatible");
            return url;
        }
    }
    static boolean validPlayback(String value){
        HttpUrl u=HttpUrl.parse(value);if(u==null||!u.isHttps()||u.port()!=443||!u.username().isEmpty()||!u.password().isEmpty())return false;
        String h=u.host();return (h.endsWith(".playback.live-video.net")||h.equals("stream.kick.com"))&&u.encodedPath().endsWith(".m3u8");
    }
}
