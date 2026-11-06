package org.grimmoir.lunera.lite;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

final class MediaFiles {
    static Bitmap bitmap(File file,int max){return bitmap(file.getAbsolutePath(),null,max);}
    static Bitmap bitmap(String path,byte[] bytes,int max){
        BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;
        if(bytes!=null)BitmapFactory.decodeByteArray(bytes,0,bytes.length,b);else BitmapFactory.decodeFile(path,b);
        b.inSampleSize=1;while(b.outWidth/b.inSampleSize>max*2 || b.outHeight/b.inSampleSize>max*2)b.inSampleSize*=2;
        b.inJustDecodeBounds=false;b.inPreferredConfig=Bitmap.Config.RGB_565;
        return bytes!=null?BitmapFactory.decodeByteArray(bytes,0,bytes.length,b):BitmapFactory.decodeFile(path,b);
    }
    static Bitmap artwork(Context context,Song song,Store store){
        File custom=new File(context.getFilesDir(),store.artFile(song));if(custom.exists())return bitmap(custom,512);
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{r.setDataSource(context,song.contentUri());byte[] b=r.getEmbeddedPicture();return b==null?null:bitmap(null,b,512);}catch(Exception|OutOfMemoryError e){return null;}finally{try{r.release();}catch(Exception ignored){}}
    }
    static void importImage(Context context,Uri uri,File target,int max)throws IOException{
        File tmp=new File(context.getCacheDir(),"image-import");
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){
            if(in==null)throw new IOException("Cannot open image");byte[] b=new byte[8192];int n,total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>32*1024*1024)throw new IOException("Image exceeds 32 MB");out.write(b,0,n);}
        }
        try{Bitmap bm=bitmap(tmp,max);if(bm==null)throw new IOException("Unsupported image");
            if(Math.max(bm.getWidth(),bm.getHeight())>max){float scale=(float)max/Math.max(bm.getWidth(),bm.getHeight());Bitmap resized=Bitmap.createScaledBitmap(bm,Math.max(1,(int)(bm.getWidth()*scale)),Math.max(1,(int)(bm.getHeight()*scale)),true);if(resized!=bm)bm.recycle();bm=resized;}
            try(OutputStream out=new FileOutputStream(target)){bm.compress(Bitmap.CompressFormat.JPEG,86,out);}finally{bm.recycle();}
        }finally{tmp.delete();}
    }
    static String lyrics(Context context,Song song,Store store){
        try{return store.read(store.lyricsFile(song));}catch(IOException ignored){}
        try(InputStream raw=context.getContentResolver().openInputStream(song.contentUri())){
            if(raw==null)return "";
            BufferedInputStream in=new BufferedInputStream(raw);in.mark(16);byte[] header=new byte[10];int n=readFully(in,header,0,10);in.reset();
            if(n>=10 && header[0]=='I'&&header[1]=='D'&&header[2]=='3')return id3(in);
            if(n>=4 && header[0]=='f'&&header[1]=='L'&&header[2]=='a'&&header[3]=='C')return flac(in);
            if(n>=4 && header[0]=='O'&&header[1]=='g'&&header[2]=='g'&&header[3]=='S')return ogg(in);
        }catch(Exception ignored){}
        return "";
    }
    private static int sync(byte[] b,int p){return(b[p]&127)<<21|(b[p+1]&127)<<14|(b[p+2]&127)<<7|(b[p+3]&127);}
    private static int big(byte[] b,int p){return(b[p]&255)<<24|(b[p+1]&255)<<16|(b[p+2]&255)<<8|(b[p+3]&255);}
    private static int little(byte[] b,int p){return(b[p]&255)|(b[p+1]&255)<<8|(b[p+2]&255)<<16|(b[p+3]&255)<<24;}
    private static int readFully(InputStream in,byte[] b,int off,int length)throws IOException{int total=0,n;while(total<length && (n=in.read(b,off+total,length-total))>0)total+=n;return total;}
    private static String id3(InputStream in)throws IOException{
        byte[] h=new byte[10];if(readFully(in,h,0,10)!=10)return "";int version=h[3],size=sync(h,6);if(size<0||size>8*1024*1024)return "";
        byte[] tag=new byte[size];if(readFully(in,tag,0,size)!=size)return "";
        if((h[5]&128)!=0){ByteArrayOutputStream cleaned=new ByteArrayOutputStream();for(int i=0;i<tag.length;i++){cleaned.write(tag[i]);if((tag[i]&255)==255 && i+1<tag.length && tag[i+1]==0)i++;}tag=cleaned.toByteArray();}
        int p=0;if((h[5]&64)!=0 && tag.length>=4)p=version==4?sync(tag,0):4+big(tag,0);
        while(p+(version==2?6:10)<=tag.length){
            int head=version==2?6:10;String id=new String(tag,p,version==2?3:4,Charset.forName("ISO-8859-1"));
            int length=version==2?((tag[p+3]&255)<<16|(tag[p+4]&255)<<8|(tag[p+5]&255)):(version==4?sync(tag,p+4):big(tag,p+4));
            if(length<=0||length>tag.length-p-head)break;
            if((id.equals("USLT")||id.equals("ULT"))&&length>4)return lyricText(tag,p+head,length);
            p+=head+length;
        }
        return "";
    }
    private static String lyricText(byte[] b,int p,int length){
        int encoding=b[p]&255,end=p+length,start=p+4;boolean wide=encoding==1||encoding==2;
        int cursor=start;while(cursor+(wide?1:0)<end){if(b[cursor]==0 && (!wide||b[cursor+1]==0)){cursor+=wide?2:1;break;}cursor+=wide?2:1;}
        if(cursor>=end)return "";
        String charset=encoding==1?"UTF-16":encoding==2?"UTF-16BE":encoding==3?"UTF-8":"ISO-8859-1";
        return new String(b,cursor,end-cursor,Charset.forName(charset)).replace("\u0000","").trim();
    }
    private static String flac(InputStream in)throws IOException{
        byte[] marker=new byte[4];readFully(in,marker,0,4);
        for(int count=0;count<100;count++){
            byte[] h=new byte[4];if(readFully(in,h,0,4)!=4)break;int type=h[0]&127,size=(h[1]&255)<<16|(h[2]&255)<<8|(h[3]&255);
            if(size>8*1024*1024)return "";byte[] block=new byte[size];if(readFully(in,block,0,size)!=size)break;
            if(type==4){String value=comments(block,0);if(!value.isEmpty())return value;}
            if((h[0]&128)!=0)break;
        }return "";
    }
    private static String comments(byte[] b,int start){
        try{int p=start;int vendor=little(b,p);p+=4;if(vendor<0||vendor>b.length-p)return "";p+=vendor;int count=little(b,p);p+=4;
            for(int i=0;i<Math.min(count,10000);i++){int length=little(b,p);p+=4;if(length<0||length>b.length-p)return "";String field=new String(b,p,length,Charset.forName("UTF-8"));p+=length;int eq=field.indexOf('=');if(eq>0){String key=field.substring(0,eq).toUpperCase(Locale.ROOT);if(key.equals("LYRICS")||key.equals("UNSYNCEDLYRICS"))return field.substring(eq+1);}}
        }catch(IndexOutOfBoundsException ignored){}return "";
    }
    private static String ogg(InputStream in)throws IOException{
        ByteArrayOutputStream packet=new ByteArrayOutputStream();int total=0;
        while(total<2*1024*1024){byte[] h=new byte[27];if(readFully(in,h,0,27)!=27 || h[0]!='O'||h[1]!='g'||h[2]!='g'||h[3]!='S')break;int n=h[26]&255;byte[] lace=new byte[n];if(readFully(in,lace,0,n)!=n)break;
            for(byte l:lace){int len=l&255;byte[] data=new byte[len];if(readFully(in,data,0,len)!=len)return "";packet.write(data);total+=len;if(len<255){byte[] b=packet.toByteArray();packet.reset();if(b.length>=7&&b[0]==3&&new String(b,1,6,Charset.forName("US-ASCII")).equals("vorbis"))return comments(b,7);if(b.length>=8&&new String(b,0,8,Charset.forName("US-ASCII")).equals("OpusTags"))return comments(b,8);}}
        }return "";
    }
}
