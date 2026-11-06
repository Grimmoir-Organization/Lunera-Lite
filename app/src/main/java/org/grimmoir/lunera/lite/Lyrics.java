package org.grimmoir.lunera.lite;

import java.util.*;
import java.util.regex.*;

/** LRC parsing is independent of Android so timing can be tested on the JVM. */
public final class Lyrics {
    public static final class Line {
        public final long time;
        public final String text;
        Line(long time, String text) { this.time = time; this.text = text; }
    }
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);
    public static List<Line> parse(String source) {
        ArrayList<Line> result = new ArrayList<>();
        Matcher offsetMatcher = OFFSET.matcher(source);
        long offset = 0;
        if(offsetMatcher.find())try{offset=Long.parseLong(offsetMatcher.group(1));if(Math.abs(offset)>86400000)offset=0;}catch(NumberFormatException ignored){}
        for (String raw : source.split("\\r?\\n")) {
            Matcher m = STAMP.matcher(raw);
            ArrayList<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                String fraction = m.group(3);
                long ms = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                stamps.add(Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000 + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (Long time : stamps) result.add(new Line(Math.max(0, time + offset), text));
        }
        Collections.sort(result, (a,b) -> Long.compare(a.time,b.time));
        return result;
    }
    public static int current(List<Line> lines, long position) {
        int lo=0,hi=lines.size()-1,result=-1;
        while(lo<=hi) {int mid=(lo+hi)>>>1; if(lines.get(mid).time<=position){result=mid;lo=mid+1;}else hi=mid-1;}
        return result;
    }
    public static String stamp(long ms) {
        ms=Math.max(0,ms);
        return String.format(Locale.US,"[%02d:%02d.%02d]",ms/60000,(ms/1000)%60,(ms%1000)/10);
    }
    public static String shift(String source, long delta) {
        List<Line> lines=parse(source);
        if(lines.isEmpty())return source;
        StringBuilder out=new StringBuilder();
        for(Line line:lines)out.append(stamp(Math.max(0,line.time+delta))).append(line.text).append('\n');
        return out.toString();
    }
}
