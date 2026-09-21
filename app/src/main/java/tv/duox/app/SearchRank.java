package tv.duox.app;

import java.util.*;

/** Orders channel search results: relevant live channels first, then offline, without duplicates. */
final class SearchRank {
    /** 0 exact, 1 prefix, 2 contains, 3 loose match returned by the platform. */
    static int relevance(TwitchAccount.Stream item,String query){
        String q=query.trim().toLowerCase(Locale.ROOT),name=item.name.toLowerCase(Locale.ROOT),slug=ChannelKey.slug(item.login).toLowerCase(Locale.ROOT);
        if(name.equals(q)||slug.equals(q))return 0;if(name.startsWith(q)||slug.startsWith(q))return 1;if(name.contains(q)||slug.contains(q))return 2;return 3;
    }
    static List<TwitchAccount.Stream> order(Collection<TwitchAccount.Stream> items,String query){return order(items,query,Collections.emptySet());}
    /** followed: channels the connected accounts follow; they go before everyone else inside the live group and inside the offline group. */
    static List<TwitchAccount.Stream> order(Collection<TwitchAccount.Stream> items,String query,Set<String> followed){
        Map<String,TwitchAccount.Stream> unique=new LinkedHashMap<>();for(TwitchAccount.Stream item:items)if(!item.login.isEmpty()){TwitchAccount.Stream old=unique.get(item.login);if(old==null||item.live&&!old.live)unique.put(item.login,item);}
        List<TwitchAccount.Stream> out=new ArrayList<>(unique.values());
        // Live matches first, then offline ones; inside each group the channels you follow come first, then most viewers (live) or most followers (offline), so a partial name already finds the big channel.
        out.sort(Comparator.<TwitchAccount.Stream>comparingInt(s->s.live&&relevance(s,query)<=2?0:1).thenComparingInt(s->relevance(s,query)<=2?0:1).thenComparingInt(s->followed.contains(s.login)?0:1).thenComparingInt(s->s.live?-s.viewers:0).thenComparingLong(s->-s.followers).thenComparingInt(s->relevance(s,query)).thenComparing(s->s.name.toLowerCase(Locale.ROOT)));
        return out;
    }
}
