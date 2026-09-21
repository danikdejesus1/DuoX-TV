package tv.duox.app;

import java.io.*;
import java.util.*;

/** Local VOD progress only. Never persists a playback URL, cookie or account token. */
final class VodHistory {
    static final int LIMIT=20;
    static final class Entry {
        final String channel,id,title,thumbnail,date,durationLabel,name;
        final long position,duration;
        Entry(String channel,String id,String title,String thumbnail,String date,String durationLabel,String name,long position,long duration){this.channel=channel;this.id=id;this.title=title;this.thumbnail=thumbnail;this.date=date;this.durationLabel=durationLabel;this.name=name;this.position=position;this.duration=duration;}
        Entry at(long position,long duration){return new Entry(channel,id,title,thumbnail,date,durationLabel,name,position,duration);}
    }
    /** A synced-VOD session is stored as one entry: id = "sync|key~vodId,key~vodId", date = wall-clock starts, durationLabel = per-pane offsets (ms) relative to pane 0. */
    static boolean isSync(Entry e){return e.id.startsWith("sync|");}
    static List<String> syncKeys(Entry e){List<String> keys=new ArrayList<>();if(isSync(e))for(String part:e.id.substring(5).split(","))if(part.contains("~"))keys.add(part.substring(0,part.indexOf('~')));return keys;}
    static List<String> syncIds(Entry e){List<String> ids=new ArrayList<>();if(isSync(e))for(String part:e.id.substring(5).split(","))if(part.contains("~"))ids.add(part.substring(part.indexOf('~')+1));return ids;}
    /** Shows in a profile when it is that channel's own VOD or a sync session that includes the channel. */
    static boolean involves(Entry e,String channel){return isSync(e)?syncKeys(e).contains(channel):e.channel.equals(channel);}
    private final LinkedHashMap<String,Entry> items=new LinkedHashMap<>();
    private static String key(String channel,String id){return channel+"\n"+id;}
    Entry get(String channel,String id){return items.get(key(channel,id));}
    void remove(String channel,String id){items.remove(key(channel,id));}
    List<Entry> recent(){List<Entry> list=new ArrayList<>(items.values());Collections.reverse(list);return list;}
    void record(Entry entry,long position,long duration,boolean ended){
        if(ended){remove(entry.channel,entry.id);return;}
        if(duration<=0||position<0)return;
        if(position<10000||duration-position<=Math.min(30000,duration/20)){remove(entry.channel,entry.id);return;}
        String key=key(entry.channel,entry.id);items.remove(key);items.put(key,entry.at(position,duration));
        while(items.size()>LIMIT)items.remove(items.keySet().iterator().next());
    }
    String encode(){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(1);out.writeInt(items.size());for(Entry e:items.values()){for(String s:new String[]{e.channel,e.id,e.title,e.thumbnail,e.date,e.durationLabel,e.name})out.writeUTF(s.length()>6000?s.substring(0,6000):s);out.writeLong(e.position);out.writeLong(e.duration);}out.close();return Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
    static VodHistory decode(String value){VodHistory history=new VodHistory();if(value==null||value.isEmpty()||value.length()>1500000)return history;try{DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)));if(in.readInt()!=1)return history;int size=in.readInt();if(size<0||size>LIMIT)return history;for(int i=0;i<size;i++){Entry e=new Entry(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readLong(),in.readLong());if(!e.channel.isEmpty()&&!e.id.isEmpty())history.record(e,e.position,e.duration,false);}return history;}catch(IOException|IllegalArgumentException e){return new VodHistory();}}
}
