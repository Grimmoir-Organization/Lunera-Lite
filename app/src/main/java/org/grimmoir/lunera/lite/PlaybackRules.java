package org.grimmoir.lunera.lite;

/** State rules shared by the player and deterministic regression checks. */
public final class PlaybackRules {
    public enum TimerResult { NONE, STOP, FINISH_SONG }
    public static TimerResult timer(long now, long deadline, boolean finishSong) {
        if(deadline<=0 || now<deadline)return TimerResult.NONE;
        return finishSong ? TimerResult.FINISH_SONG : TimerResult.STOP;
    }
    public static int next(int index, int size, int repeat, boolean completion) {
        if(size==0)return -1;
        if(completion && repeat==2)return Math.max(0,index);
        if(index+1<size)return index+1;
        return repeat==1 || !completion ? 0 : -1;
    }
    public static boolean excluded(String path, String roots) {
        String normalized=path.replace('\\','/');
        for(String line:roots.split("\\r?\\n")) {
            String root=line.trim().replace('\\','/');
            while(root.endsWith("/") && root.length()>1)root=root.substring(0,root.length()-1);
            if(!root.isEmpty() && (normalized.equals(root) || normalized.startsWith(root.equals("/")?root:root+"/")))return true;
        }
        return false;
    }
}
