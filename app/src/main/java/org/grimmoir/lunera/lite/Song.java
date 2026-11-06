package org.grimmoir.lunera.lite;

import android.net.Uri;
import org.json.*;

public final class Song {
    public String uri,title,artist,path;
    public long duration,modified;
    public Song(String uri,String title,String artist,String path,long duration,long modified){this.uri=uri;this.title=title;this.artist=artist;this.path=path;this.duration=duration;this.modified=modified;}
    public String folder(){int i=path.lastIndexOf('/');return i>0?path.substring(0,i):"Unknown folder";}
    public Uri contentUri(){return Uri.parse(uri);}
    public JSONObject json() throws JSONException {return new JSONObject().put("uri",uri).put("title",title).put("artist",artist).put("path",path).put("duration",duration).put("modified",modified);}
    public static Song from(JSONObject j){return new Song(j.optString("uri"),j.optString("title"),j.optString("artist"),j.optString("path"),j.optLong("duration"),j.optLong("modified"));}
}
