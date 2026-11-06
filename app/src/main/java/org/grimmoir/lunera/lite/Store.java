package org.grimmoir.lunera.lite;

import android.content.*;
import android.database.Cursor;
import android.provider.MediaStore;
import android.net.Uri;
import java.util.*;
import java.io.*;
import org.json.*;

public final class Store {
    final Context context;
    final android.content.SharedPreferences prefs;
    public Store(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("lite",0);}
    public List<Song> scan(){
        ArrayList<Song> songs=new ArrayList<>();
        String[] cols={"_id","title","artist","_data","duration","date_modified"};
        long minimum=prefs.getInt("minimumSeconds",0)*1000L;
        try(Cursor c=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,null,null,"title COLLATE NOCASE ASC")){
            if(c!=null)while(c.moveToNext()){
                String path=c.getString(3); if(path==null)path="";
                long duration=c.getLong(4);
                if(duration<minimum || PlaybackRules.excluded(path,prefs.getString("excluded","")))continue;
                String title=c.getString(1),artist=c.getString(2);
                if(title==null || title.isEmpty())title=path.substring(path.lastIndexOf('/')+1);
                if(artist==null || "<unknown>".equals(artist))artist="Unknown artist";
                Uri uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,c.getLong(0));
                songs.add(new Song(uri.toString(),title,artist,path,duration,c.getLong(5)));
            }
        }
        saveSongs("library.json",songs);return songs;
    }
    public synchronized void saveSongs(String file,List<Song> songs){
        try{JSONArray a=new JSONArray();for(Song s:songs)a.put(s.json());write(file,a.toString());}catch(Exception ignored){}
    }
    public List<Song> loadSongs(String file){ArrayList<Song> out=new ArrayList<>();try{JSONArray a=new JSONArray(read(file));for(int i=0;i<a.length();i++)out.add(Song.from(a.getJSONObject(i)));}catch(Exception ignored){}return out;}
    public synchronized void write(String file,String text)throws IOException{
        File target=new File(context.getFilesDir(),file), temp=new File(context.getFilesDir(),file+".tmp");
        try(OutputStream o=new FileOutputStream(temp)){o.write(text.getBytes("UTF-8"));}
        if(!temp.renameTo(target))throw new IOException("Could not save "+file);
    }
    public String read(String file)throws IOException{
        try(InputStream in=new FileInputStream(new File(context.getFilesDir(),file))){return readText(in,2*1024*1024);}
    }
    public static String readText(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("File is too large");out.write(b,0,n);}
        byte[] bytes=out.toByteArray();
        if(bytes.length>=2 && ((bytes[0]==-1&&bytes[1]==-2)||(bytes[0]==-2&&bytes[1]==-1)))return new String(bytes,"UTF-16");
        return new String(bytes,"UTF-8").replace("\uFEFF","");
    }
    public boolean favourite(Song s){return prefs.getStringSet("favourites",Collections.emptySet()).contains(s.uri);}
    public void toggleFavourite(Song s){Set<String> set=new HashSet<>(prefs.getStringSet("favourites",Collections.emptySet()));if(!set.remove(s.uri))set.add(s.uri);prefs.edit().putStringSet("favourites",set).apply();}
    public JSONObject playlists(){try{return new JSONObject(read("playlists.json"));}catch(Exception e){return new JSONObject();}}
    public void savePlaylists(JSONObject p)throws IOException{write("playlists.json",p.toString());}
    public String key(Song s){return Integer.toHexString(s.uri.hashCode())+"-"+s.uri.replaceAll("[^0-9]","");}
    public String lyricsFile(Song s){return "lyrics-"+key(s)+".lrc";}
    public String artFile(Song s){return "art-"+key(s)+".jpg";}
}
