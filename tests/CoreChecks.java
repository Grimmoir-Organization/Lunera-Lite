package org.grimmoir.lunera.lite;
import java.util.*;
public final class CoreChecks {
    private static int checks;
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        List<Lyrics.Line> lines=Lyrics.parse("[offset:-250]\n[00:01.50][00:02.123]Hello\n[00:03]World\n[ar:Artist]");
        check(lines.size()==3,"multiple timestamps");check(lines.get(0).time==1250,"negative offset");check(lines.get(1).time==1873,"millisecond precision");check(lines.get(2).time==2750,"whole seconds");
        check(Lyrics.current(lines,0)==-1,"before first line");check(Lyrics.current(lines,1250)==0,"exact timestamp");check(Lyrics.current(lines,1872)==0,"between timestamps");check(Lyrics.current(lines,9000)==2,"after last line");
        check(Lyrics.parse("plain lyrics").isEmpty(),"plain text");check(Lyrics.parse("").isEmpty(),"empty lyrics");check(Lyrics.parse("[00:01.5]x").get(0).time==1500,"tenths precision");
        check(Lyrics.parse("[00:01.05]x").get(0).time==1050,"hundredths precision");check(Lyrics.shift("[00:00.10]x",-250).startsWith("[00:00.00]"),"clamp negative shift");check(Lyrics.shift("plain",500).equals("plain"),"preserve plain lyrics");
        check(Lyrics.shift("[offset:100]\n[00:01.00]x",250).startsWith("[00:01.35]"),"offset applied once");
        check(Lyrics.stamp(61020).equals("[01:01.02]"),"timestamp formatting");
        check(Lyrics.parse("[offset:999999999999999999999999]\n[00:01.00]x").get(0).time==1000,"malformed offset does not crash");
        check(PlaybackRules.timer(10,0,false)==PlaybackRules.TimerResult.NONE,"disabled timer");check(PlaybackRules.timer(999,1000,false)==PlaybackRules.TimerResult.NONE,"timer before deadline");
        check(PlaybackRules.timer(1000,1000,false)==PlaybackRules.TimerResult.STOP,"immediate stop at deadline");check(PlaybackRules.timer(1001,1000,true)==PlaybackRules.TimerResult.FINISH_SONG,"finish song after deadline");
        check(PlaybackRules.next(1,3,0,true)==2,"advance");check(PlaybackRules.next(2,3,0,true)==-1,"stop at queue end");check(PlaybackRules.next(2,3,1,true)==0,"repeat all");check(PlaybackRules.next(1,3,2,true)==1,"repeat one");check(PlaybackRules.next(1,3,2,false)==2,"manual next overrides repeat one");check(PlaybackRules.next(2,3,0,false)==0,"manual next wraps");check(PlaybackRules.next(0,0,0,true)==-1,"empty queue");
        check(PlaybackRules.excluded("/Music/Private/a.mp3","/Music/Private"),"excluded subfolder");check(!PlaybackRules.excluded("/Music/PrivateOther/a.mp3","/Music/Private"),"folder boundary");check(PlaybackRules.excluded("/Music/a.mp3","/Music/\n/Other"),"trailing slash");check(!PlaybackRules.excluded("/Music/a.mp3","\n"),"empty filters");check(PlaybackRules.excluded("/Music/a.mp3","/"),"root filter");
        System.out.println("PASS: "+checks+" core regression checks");
    }
}
