package tv.duox.app;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class SearchRankTest {
    private static TwitchAccount.Stream item(String login,String name,boolean live,int viewers){TwitchAccount.Stream s=new TwitchAccount.Stream();s.login=login;s.name=name;s.live=live;s.viewers=viewers;return s;}
    @Test public void liveRelevantMatchesComeBeforeOffline(){
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(item("auronplay","AuronPlay",false,0),item("auron_fan","Auron_fan",true,50),item("kick:auronplay2","auronplay2",true,900)),"auron");
        assertEquals("kick:auronplay2",out.get(0).login);assertEquals("auron_fan",out.get(1).login);assertEquals("auronplay",out.get(2).login);
    }
    @Test public void liveChannelsAreOrderedByViewers(){
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(item("aurons","Aurons",true,5000),item("auron","Auron",true,10)),"auron");
        assertEquals("aurons",out.get(0).login);
    }
    @Test public void offlineMatchesAreOrderedByFollowers(){
        TwitchAccount.Stream small=item("auron_small","Auron_small",false,0),big=item("auronplay","AuronPlay",false,0),mid=item("auron_mid","Auron_mid",false,0);small.followers=10;big.followers=17000000;mid.followers=5000;
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(small,mid,big),"aur");
        assertEquals("auronplay",out.get(0).login);assertEquals("auron_mid",out.get(1).login);assertEquals("auron_small",out.get(2).login);
    }
    @Test public void followedChannelsGoFirstInsideEachGroup(){
        TwitchAccount.Stream bigOffline=item("auronplay","AuronPlay",false,0),myOffline=item("aurelia","Aurelia",false,0),bigLive=item("auronlive","AuronLive",true,9000),myLive=item("aurmine","AurMine",true,5);bigOffline.followers=17000000;myOffline.followers=3;
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(bigOffline,myOffline,bigLive,myLive),"aur",new HashSet<>(Arrays.asList("aurelia","aurmine")));
        assertEquals("aurmine",out.get(0).login);assertEquals("auronlive",out.get(1).login);assertEquals("aurelia",out.get(2).login);assertEquals("auronplay",out.get(3).login);
    }
    @Test public void looseMatchesGoAfterRelevantOfflineOnes(){
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(item("zzz","Something",true,99999),item("kick:auron","auron",false,0)),"auron");
        assertEquals("kick:auron",out.get(0).login);
    }
    @Test public void duplicatesAreRemovedPreferringLive(){
        List<TwitchAccount.Stream> out=SearchRank.order(Arrays.asList(item("abc","abc",false,0),item("abc","abc",true,3)),"abc");
        assertEquals(1,out.size());assertTrue(out.get(0).live);
    }
}
