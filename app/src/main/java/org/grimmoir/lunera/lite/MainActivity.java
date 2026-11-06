package org.grimmoir.lunera.lite;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Small framework UI: no Compose runtime, remote artwork, trackers or animation engine. */
public final class MainActivity extends Activity implements PlaybackService.Listener {
    private Store store;
    private PlaybackService service;
    private boolean bound=false,visible=false,dead=false,loading=false;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<Song> library=new ArrayList<>(),shown=new ArrayList<>();
    private final ArrayList<String> folderRows=new ArrayList<>(),playlistRows=new ArrayList<>();
    private FrameLayout root;
    private LinearLayout page,content,mini;
    private TextView title,subtitle,miniTitle,miniArtist,playerTitle,playerArtist,timeLabel,lyricsText,timerLabel;
    private Button miniPlay,playerPlay,shuffleButton,repeatButton;
    private EditText search;
    private ListView list;
    private SongAdapter adapter;
    private SeekBar seek;
    private ImageView playerArt;
    private ScrollView lyricScroll;
    private String tab="All songs",screen="library",folder="",playlist="",query="",lyricsSource="",lyricsUri="",artUri="";
    private Song importTarget;
    private int foreground,background,panel,muted,accent,lastLyric=-2;
    private boolean dark=false,dragging=false;
    private List<Lyrics.Line> lyricLines=new ArrayList<>();
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=((PlaybackService.LocalBinder)binder).getService();bound=true;if(visible)service.addListener(MainActivity.this);render();}
        @Override public void onServiceDisconnected(ComponentName name){service=null;bound=false;render();}
    };
    private final Runnable tick=new Runnable(){@Override public void run(){if(!visible||dead)return;updateProgress();main.postDelayed(this,"lyrics".equals(screen)?400:1000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new Store(this);
        if(saved!=null){tab=saved.getString("tab","All songs");folder=saved.getString("folder","");playlist=saved.getString("playlist","");screen=saved.getString("screen","library");String pending=saved.getString("importTarget");if(pending!=null)try{importTarget=Song.from(new JSONObject(pending));}catch(Exception ignored){}}
        library.addAll(store.loadSongs("library.json"));
        bindService(new Intent(this,PlaybackService.class),connection,BIND_AUTO_CREATE);render();
        if(!hasAudioPermission())requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE},10);
        else if(library.isEmpty())scan();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);out.putString("folder",folder);out.putString("playlist",playlist);out.putString("screen",screen);if(importTarget!=null)try{out.putString("importTarget",importTarget.json().toString());}catch(Exception ignored){}}
    @Override protected void onStart(){super.onStart();visible=true;if(service!=null)service.addListener(this);main.removeCallbacks(tick);main.post(tick);}
    @Override protected void onStop(){visible=false;main.removeCallbacks(tick);if(service!=null)service.removeListener(this);super.onStop();}
    @Override protected void onDestroy(){dead=true;main.removeCallbacksAndMessages(null);if(service!=null)service.removeListener(this);try{unbindService(connection);}catch(IllegalArgumentException ignored){}io.shutdownNow();super.onDestroy();}
    @Override public void changed(){if(!visible||dead)return;if(screen.equals("queue")){render();return;}updateMini();updateProgress();if(adapter!=null)adapter.notifyDataSetChanged();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String label,int size,int color){TextView t=new TextView(this);t.setText(label);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(foreground);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),dp(4),dp(10),dp(4));b.setBackground(surface(panel,8));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    private void weight(LinearLayout row,View v){row.addView(v,new LinearLayout.LayoutParams(0,dp(48),1));}
    private void gap(LinearLayout parent,int height){Space s=new Space(this);parent.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    private void palette(){
        String theme=store.prefs.getString("theme","system");dark=theme.equals("dark")||(theme.equals("system")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        background=dark?0xFF131418:0xFFF8F7F9;panel=dark?0xFF22242B:0xFFECEAF0;foreground=dark?0xFFF3F1F6:0xFF202027;muted=dark?0xFFB8B4C2:0xFF605B69;accent=store.prefs.getInt("accent",0xFFB7234D);
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);getWindow().setWindowAnimations(0);
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        int flags=dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(Build.VERSION.SDK_INT>=26&&!dark)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void render(){
        if(dead)return;palette();adapter=null;playerTitle=null;playerArtist=null;playerPlay=null;playerArt=null;seek=null;timeLabel=null;lyricsText=null;timerLabel=null;miniTitle=null;miniArtist=null;miniPlay=null;
        root=new FrameLayout(this);root.setBackgroundColor(background);root.setFitsSystemWindows(true);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        File bg=new File(getFilesDir(),"background.jpg");
        if(bg.exists()){Bitmap bm=MediaFiles.bitmap(bg,1280);if(bm!=null){ImageView image=new ImageView(this);image.setImageBitmap(bm);image.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(image,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(dark?0xD0131418:0xDCF8F7F9);root.addView(shade,new FrameLayout.LayoutParams(-1,-1));}}
        page=vertical();page.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(page,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        LinearLayout header=horizontal();
        if(!screen.equals("library"))header.addView(button("Back",this::back),new LinearLayout.LayoutParams(dp(68),dp(48)));
        LinearLayout names=vertical();title=text(screen.equals("library")?"Lunera Lite":screen.equals("player")?"Now playing":screen.equals("lyrics")?"Lyrics":screen.equals("queue")?"Playback queue":"Settings",24,foreground);title.setTypeface(null,Typeface.BOLD);subtitle=text(screen.equals("library")?"Your music, kept simple":"",12,muted);names.addView(title);if(screen.equals("library"))names.addView(subtitle);
        title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);header.addView(names,new LinearLayout.LayoutParams(0,dp(62),1));
        if(screen.equals("library"))header.addView(button("More",this::libraryMenu),new LinearLayout.LayoutParams(dp(65),dp(48)));
        page.addView(header);gap(page,8);
        content=vertical();page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        switch(screen){case "player":renderPlayer();break;case "lyrics":renderLyrics();break;case "queue":renderQueue();break;case "settings":renderSettings();break;default:renderLibrary();}
        if(!screen.equals("player")){gap(page,8);mini=vertical();mini.setPadding(dp(12),dp(8),dp(8),dp(8));mini.setBackground(surface(panel,12));LinearLayout row=horizontal();LinearLayout track=vertical();miniTitle=text("Choose a song",15,foreground);miniTitle.setMaxLines(1);miniTitle.setEllipsize(TextUtils.TruncateAt.END);miniArtist=text("",12,muted);miniArtist.setMaxLines(1);track.addView(miniTitle);track.addView(miniArtist);track.setOnClickListener(v->open("player"));row.addView(track,new LinearLayout.LayoutParams(0,dp(48),1));miniPlay=button("Play",this::toggle);row.addView(miniPlay,new LinearLayout.LayoutParams(dp(72),dp(48)));mini.addView(row);page.addView(mini);}
        updateMini();updateProgress();
    }
    private void open(String name){hideKeyboard();screen=name;render();}
    private void back(){if(screen.equals("lyrics"))open("player");else if(!screen.equals("library"))open("library");else if(!playlist.isEmpty()){playlist="";render();}else if(!folder.isEmpty()){int slash=folder.lastIndexOf('/');folder=slash>0?folder.substring(0,slash):"";render();}else super.onBackPressed();}
    @Override public void onBackPressed(){back();}
    private void hideKeyboard(){View view=getCurrentFocus();if(view!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}
    private boolean hasAudioPermission(){return checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==10){if(hasAudioPermission())scan();else{render();toast("Allow audio access to show your local songs.");}}}
    private void scan(){
        if(loading)return;if(!hasAudioPermission()){requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE},10);return;}
        loading=true;if(subtitle!=null)subtitle.setText("Scanning local music…");
        io.execute(()->{try{List<Song> result=store.scan();main.post(()->{if(dead)return;loading=false;library.clear();library.addAll(result);if(screen.equals("library"))refreshLibrary();toast(result.size()+" songs found");});}catch(Exception e){main.post(()->{loading=false;if(!dead)toast("Could not scan music: "+e.getMessage());});}});
    }
    private void renderLibrary(){
        LinearLayout tabs=horizontal();for(String name:new String[]{"All songs","Folders","Favourites","Playlists"}){Button b=button(name,()->{tab=name;folder="";playlist="";query="";render();});if(tab.equals(name)){b.setBackground(surface(accent,8));b.setTextColor(android.graphics.Color.WHITE);}weight(tabs,b);}content.addView(tabs);gap(content,8);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(foreground);search.setHintTextColor(muted);search.setTextSize(15);search.setHint("Search songs");search.setBackground(surface(panel,8));search.setPadding(dp(12),0,dp(12),0);search.setText(query);content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));gap(content,8);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();refreshLibrary();}public void afterTextChanged(Editable e){}});
        LinearLayout tools=horizontal();Button focus=button("Focus",this::focusCurrent);weight(tools,focus);if(tab.equals("Folders"))weight(tools,button("Up",()->{if(!folder.isEmpty()){int p=folder.lastIndexOf('/');folder=p>0?folder.substring(0,p):"";refreshLibrary();}}));if(tab.equals("Playlists"))weight(tools,button("New playlist",this::createPlaylist));content.addView(tools);
        list=new ListView(this);list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setScrollingCacheEnabled(false);list.setEmptyView(emptyView("No songs here. Use More → Rescan, or check your filters."));content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new SongAdapter();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{if(pos<folderRows.size()){folder=folderRows.get(pos);refreshLibrary();}else if(pos<folderRows.size()+playlistRows.size()){playlist=playlistRows.get(pos-folderRows.size());refreshLibrary();}else{int song=pos-folderRows.size()-playlistRows.size();if(service!=null&&song<shown.size()){askNotification();service.playList(new ArrayList<>(shown),song);}}});
        list.setOnItemLongClickListener((p,v,pos,id)->{if(pos<folderRows.size()){String path=folderRows.get(pos);new AlertDialog.Builder(this).setTitle(path).setItems(new String[]{"Open folder","Exclude folder and subfolders"},(d,w)->{if(w==0){folder=path;refreshLibrary();}else{String current=store.prefs.getString("excluded","");store.prefs.edit().putString("excluded",current+"\n"+path).apply();scan();}}).show();}else if(pos<folderRows.size()+playlistRows.size())playlistMenu(playlistRows.get(pos-folderRows.size()));else songMenu(shown.get(pos-folderRows.size()-playlistRows.size()));return true;});refreshLibrary();
    }
    private View emptyView(String label){TextView empty=text(label,15,muted);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(12),dp(12),dp(12));content.addView(empty);return empty;}
    private void refreshLibrary(){
        if(adapter==null||!screen.equals("library"))return;shown.clear();folderRows.clear();playlistRows.clear();String q=query.trim().toLowerCase(Locale.ROOT);
        Set<String> playlistUris=new HashSet<>();if(!playlist.isEmpty()){JSONArray a=store.playlists().optJSONArray(playlist);if(a!=null)for(int i=0;i<a.length();i++)playlistUris.add(a.optString(i));}
        TreeSet<String> children=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if(tab.equals("Playlists")&&playlist.isEmpty()&&q.isEmpty()){Iterator<String> keys=store.playlists().keys();while(keys.hasNext())playlistRows.add(keys.next());Collections.sort(playlistRows,String.CASE_INSENSITIVE_ORDER);}
        else for(Song s:library){
            if(!q.isEmpty()&&!(s.title+" "+s.artist+" "+s.path).toLowerCase(Locale.ROOT).contains(q))continue;
            if(tab.equals("Favourites")&&!store.favourite(s))continue;
            if(tab.equals("Playlists")&&!playlist.isEmpty()&&!playlistUris.contains(s.uri))continue;
            if(tab.equals("Folders")&&q.isEmpty()){
                String path=s.folder();if(folder.isEmpty()){String[] parts=path.split("/");String rootPath="";for(String part:parts){if(!part.isEmpty()){rootPath+="/"+part;if(rootPath.equals("/storage")||rootPath.equals("/storage/emulated"))continue;break;}}if(!rootPath.isEmpty())children.add(rootPath);continue;}
                if(path.equals(folder)){shown.add(s);continue;}
                if(path.startsWith(folder+"/")){String rest=path.substring(folder.length()+1);int slash=rest.indexOf('/');children.add(folder+"/"+(slash<0?rest:rest.substring(0,slash)));}continue;
            }
            shown.add(s);
        }
        folderRows.addAll(children);sortSongs(shown);
        subtitle.setText(loading?"Scanning local music…":!playlist.isEmpty()?playlist:!folder.isEmpty()?folder:library.size()+" local songs");adapter.notifyDataSetChanged();
    }
    private void sortSongs(List<Song> songs){int sort=store.prefs.getInt("sort",0);Collections.sort(songs,(a,b)->{int comparison=sort==2?a.artist.compareToIgnoreCase(b.artist):sort==3?Long.compare(b.modified,a.modified):sort==4?Long.compare(b.duration,a.duration):a.title.compareToIgnoreCase(b.title);return sort==1?-comparison:comparison;});}
    private final class SongAdapter extends BaseAdapter{
        public int getCount(){return folderRows.size()+playlistRows.size()+shown.size();}public Object getItem(int p){return p;}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,android.view.ViewGroup parent){
            LinearLayout row;if(convert instanceof LinearLayout)row=(LinearLayout)convert;else{row=vertical();row.setPadding(dp(12),dp(10),dp(12),dp(10));TextView a=text("",16,foreground);a.setMaxLines(1);a.setEllipsize(TextUtils.TruncateAt.END);TextView b=text("",12,muted);b.setMaxLines(1);b.setEllipsize(TextUtils.TruncateAt.END);row.addView(a);row.addView(b);row.setMinimumHeight(dp(64));}
            TextView a=(TextView)row.getChildAt(0),b=(TextView)row.getChildAt(1);a.setTextColor(foreground);row.setBackgroundColor(Color.TRANSPARENT);
            if(pos<folderRows.size()){String f=folderRows.get(pos);a.setText("Folder · "+(f.equals("/storage/emulated/0")?"Internal storage":f.substring(f.lastIndexOf('/')+1)));b.setText(f);}
            else if(pos<folderRows.size()+playlistRows.size()){String name=playlistRows.get(pos-folderRows.size());a.setText(name);JSONArray entries=store.playlists().optJSONArray(name);b.setText((entries==null?0:entries.length())+" songs · hold to edit");}
            else{Song song=shown.get(pos-folderRows.size()-playlistRows.size());a.setText(song.title);b.setText((store.favourite(song)?"Favourite · ":"")+song.artist+" · "+clock(song.duration));if(service!=null&&service.current()!=null&&song.uri.equals(service.current().uri)){a.setTextColor(accent);row.setBackground(surface(dark?0x4422242B:0x77ECEAF0,8));}}
            return row;
        }
    }
    private void focusCurrent(){Song song=service==null?null:service.current();if(song==null){toast("Play a song first.");return;}tab="All songs";folder="";playlist="";query="";render();for(int i=0;i<shown.size();i++)if(shown.get(i).uri.equals(song.uri)){list.setSelection(i);toast("Located "+song.title);return;}toast("Last song is outside the current library filters.");}
    private void libraryMenu(){new AlertDialog.Builder(this).setTitle("Library").setItems(new String[]{"Sort songs","Rescan library","Folder and duration filters","Playback queue","Sleep timer","Settings"},(d,w)->{switch(w){case 0:sortDialog();break;case 1:scan();break;case 2:filters();break;case 3:open("queue");break;case 4:timerDialog();break;case 5:open("settings");break;}}).show();}
    private void sortDialog(){new AlertDialog.Builder(this).setTitle("Sort songs").setSingleChoiceItems(new String[]{"Title A–Z","Title Z–A","Artist","Recently modified","Longest first"},store.prefs.getInt("sort",0),(d,w)->{store.prefs.edit().putInt("sort",w).apply();refreshLibrary();d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void filters(){LinearLayout fields=vertical();fields.setPadding(dp(20),0,dp(20),0);fields.addView(text("Exclude folders and their subfolders (one path per line)",14,muted));EditText paths=new EditText(this);paths.setText(store.prefs.getString("excluded",""));paths.setHint("/storage/emulated/0/Recordings");paths.setMinLines(3);fields.addView(paths);fields.addView(text("Minimum song length in seconds (0 = include all)",14,muted));EditText minimum=new EditText(this);minimum.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);minimum.setText(""+store.prefs.getInt("minimumSeconds",0));fields.addView(minimum);new AlertDialog.Builder(this).setTitle("Library filters").setView(fields).setPositiveButton("Save and rescan",(d,w)->{int value=0;try{value=Math.max(0,Math.min(86400,Integer.parseInt(minimum.getText().toString())));}catch(Exception ignored){}store.prefs.edit().putString("excluded",paths.getText().toString()).putInt("minimumSeconds",value).apply();scan();}).setNegativeButton("Cancel",null).show();}
    private void songMenu(Song song){ArrayList<String> choices=new ArrayList<>(Arrays.asList(store.favourite(song)?"Remove favourite":"Add favourite","Add to queue","Add to playlist","Choose local artwork","Remove custom artwork","Lyrics"));if(!playlist.isEmpty())choices.add("Remove from this playlist");new AlertDialog.Builder(this).setTitle(song.title).setItems(choices.toArray(new String[0]),(d,w)->{switch(w){case 0:store.toggleFavourite(song);refreshLibrary();break;case 1:if(service!=null)service.enqueue(song);break;case 2:addToPlaylist(song);break;case 3:importTarget=song;pick("image/*",42);break;case 4:new File(getFilesDir(),store.artFile(song)).delete();artUri="";if(screen.equals("player"))loadArt();toast("Custom artwork removed");break;case 5:if(service==null||service.current()==null||!service.current().uri.equals(song.uri)){toast("Play this song first to view or edit its lyrics.");}else open("lyrics");break;case 6:removeFromPlaylist(song);break;}}).show();}
    private void createPlaylist(){prompt("New playlist","",name->{if(name.trim().isEmpty())return;JSONObject p=store.playlists();if(p.has(name)){toast("That playlist already exists.");return;}try{p.put(name,new JSONArray());store.savePlaylists(p);refreshLibrary();}catch(Exception e){toast("Could not save playlist");}});}
    private interface TextAction{void accept(String value);}
    private void prompt(String title,String initial,TextAction action){EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setSelectAllOnFocus(true);new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Save",(d,w)->action.accept(input.getText().toString().trim())).setNegativeButton("Cancel",null).show();}
    private void addToPlaylist(Song song){JSONObject p=store.playlists();ArrayList<String> names=new ArrayList<>();Iterator<String> it=p.keys();while(it.hasNext())names.add(it.next());Collections.sort(names,String.CASE_INSENSITIVE_ORDER);names.add("Create new playlist…");new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names.toArray(new String[0]),(d,w)->{if(w==names.size()-1){prompt("New playlist","",name->{if(name.isEmpty())return;addSongToPlaylist(name,song);});}else addSongToPlaylist(names.get(w),song);}).show();}
    private void addSongToPlaylist(String name,Song song){try{JSONObject p=store.playlists();JSONArray a=p.optJSONArray(name);if(a==null)a=new JSONArray();for(int i=0;i<a.length();i++)if(a.optString(i).equals(song.uri)){toast("Already in playlist");return;}a.put(song.uri);p.put(name,a);store.savePlaylists(p);toast("Added to "+name);refreshLibrary();}catch(Exception e){toast("Could not save playlist");}}
    private void removeFromPlaylist(Song song){try{JSONObject p=store.playlists();JSONArray a=p.optJSONArray(playlist),next=new JSONArray();if(a!=null)for(int i=0;i<a.length();i++)if(!a.optString(i).equals(song.uri))next.put(a.optString(i));p.put(playlist,next);store.savePlaylists(p);refreshLibrary();}catch(Exception e){toast("Could not edit playlist");}}
    private void playlistMenu(String name){new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Open and edit songs","Rename","Delete playlist"},(d,w)->{if(w==0){playlist=name;refreshLibrary();}else if(w==1)prompt("Rename playlist",name,next->{if(next.isEmpty()||next.equals(name))return;try{JSONObject p=store.playlists();if(p.has(next)){toast("That name is already used");return;}p.put(next,p.get(name));p.remove(name);store.savePlaylists(p);refreshLibrary();}catch(Exception e){toast("Could not rename playlist");}});else new AlertDialog.Builder(this).setTitle("Delete "+name+"?").setMessage("Your music files will stay on the phone.").setPositiveButton("Delete",(dd,ww)->{try{JSONObject p=store.playlists();p.remove(name);store.savePlaylists(p);refreshLibrary();}catch(Exception e){toast("Could not delete playlist");}}).setNegativeButton("Cancel",null).show();}).show();}
    private void renderPlayer(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        playerArt=new ImageView(this);playerArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);playerArt.setBackground(surface(panel,12));body.addView(playerArt,new LinearLayout.LayoutParams(-1,dp(190)));gap(body,14);
        playerTitle=text("Choose a song",22,foreground);playerTitle.setMaxLines(2);playerTitle.setTypeface(null,Typeface.BOLD);playerArtist=text("",14,muted);body.addView(playerTitle);gap(body,5);body.addView(playerArtist);gap(body,12);
        seek=new SeekBar(this);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){dragging=false;if(service!=null)service.seek(s.getProgress());}public void onProgressChanged(SeekBar s,int value,boolean fromUser){if(fromUser&&timeLabel!=null)timeLabel.setText(clock(value)+" / "+clock(service==null?0:service.duration()));}});body.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));timeLabel=text("0:00 / 0:00",12,muted);body.addView(timeLabel);
        LinearLayout controls=horizontal();weight(controls,button("Previous",()->{if(service!=null)service.previous();}));playerPlay=button("Play",this::toggle);weight(controls,playerPlay);weight(controls,button("Next",()->{if(service!=null)service.next(false);}));body.addView(controls);gap(body,8);
        LinearLayout modes=horizontal();shuffleButton=button("Shuffle",()->{if(service!=null){service.shuffle=!service.shuffle;service.modes();}});repeatButton=button("Repeat",()->{if(service!=null){service.repeat=(service.repeat+1)%3;service.modes();}});weight(modes,shuffleButton);weight(modes,repeatButton);body.addView(modes);gap(body,8);
        LinearLayout extras=horizontal();weight(extras,button("Queue",()->open("queue")));weight(extras,button("Lyrics",()->open("lyrics")));weight(extras,button("Timer",this::timerDialog));body.addView(extras);gap(body,8);body.addView(button("Song options",()->{if(service!=null&&service.current()!=null)songMenu(service.current());}));timerLabel=text("",13,muted);body.addView(timerLabel);artUri="";loadArt();
    }
    private void askNotification(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!store.prefs.getBoolean("notificationAsked",false)){store.prefs.edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);}}
    private void toggle(){if(service==null){toast("Connecting to the player…");return;}if(service.current()==null){toast("Choose a song from the library.");return;}askNotification();service.toggle();}
    private void updateMini(){
        if(service==null)return;Song song=service.current();boolean playing=service.isPlaying();
        if(miniTitle!=null){miniTitle.setText(song==null?"Choose a song":song.title);miniArtist.setText(!service.error.isEmpty()?service.error:song==null?"Local playback · Android 6+":song.artist);miniPlay.setText(playing?"Pause":service.isPreparing()?"Wait":"Play");}
        if(playerTitle!=null){playerTitle.setText(song==null?"Choose a song":song.title);playerArtist.setText(!service.error.isEmpty()?service.error:song==null?"":song.artist);playerPlay.setText(playing?"Pause":service.isPreparing()?"Loading":"Play");shuffleButton.setText("Shuffle: "+(service.shuffle?"on":"off"));repeatButton.setText("Repeat: "+new String[]{"off","all","one"}[service.repeat]);if(song!=null&&!song.uri.equals(artUri))loadArt();}
        if(screen.equals("lyrics")&&song!=null&&!song.uri.equals(lyricsUri))loadLyrics();
    }
    private void updateProgress(){if(service==null)return;int pos=service.position();if(seek!=null&&!dragging){seek.setMax(Math.max(1,service.duration()));seek.setProgress(pos);timeLabel.setText(clock(pos)+" / "+clock(service.duration()));}if(timerLabel!=null)timerLabel.setText(service.waitingForEnd?"Timer: stopping at the end of this song":service.timerDeadline>0?"Sleep timer: "+clock(Math.max(0,service.timerDeadline-SystemClock.elapsedRealtime())):"");if(lyricsText!=null&&!lyricLines.isEmpty()){int current=Lyrics.current(lyricLines,pos);if(current!=lastLyric){lastLyric=current;displayLyrics();}}}
    private void loadArt(){if(playerArt==null||service==null||service.current()==null)return;Song song=service.current();artUri=song.uri;playerArt.setImageResource(R.drawable.ic_lunera);io.execute(()->{Bitmap bm=MediaFiles.artwork(this,song,store);main.post(()->{if(dead||playerArt==null||!song.uri.equals(artUri)){if(bm!=null)bm.recycle();return;}if(bm!=null)playerArt.setImageBitmap(bm);});});}
    private void renderQueue(){
        if(service==null){content.addView(text("Connecting to player…",15,muted));return;}
        content.addView(text("Tap to play · hold to move or remove",13,muted));ListView q=new ListView(this);q.setDivider(null);ArrayList<String> labels=new ArrayList<>();for(int i=0;i<service.queue.size();i++){Song s=service.queue.get(i);labels.add((i==service.index?"Playing · ":"")+s.title+"\n"+s.artist);}q.setAdapter(simpleAdapter(labels));content.addView(q,new LinearLayout.LayoutParams(-1,0,1));q.setOnItemClickListener((p,v,n,id)->{service.select(n);render();});q.setOnItemLongClickListener((p,v,n,id)->{new AlertDialog.Builder(this).setTitle(service.queue.get(n).title).setItems(new String[]{"Move up","Move down","Remove from queue"},(d,w)->{if(w==0)service.move(n,n-1);else if(w==1)service.move(n,n+1);else service.remove(n);render();}).show();return true;});
    }
    private ArrayAdapter<String> simpleAdapter(List<String> labels){return new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){@Override public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(foreground);t.setTextSize(15);t.setMinHeight(dp(56));return t;}};}
    private void timerDialog(){
        if(service==null)return;LinearLayout body=vertical();body.setPadding(dp(20),dp(6),dp(20),0);EditText minutes=new EditText(this);minutes.setHint("Minutes");minutes.setText("30");minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);body.addView(minutes);CheckBox finish=new CheckBox(this);finish.setText("Finish the song playing when time expires");finish.setChecked(service.finishSong||service.waitingForEnd);body.addView(finish);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("Sleep timer").setView(body).setPositiveButton("Start",null).setNegativeButton("Cancel",null);
        if(service.timerDeadline>0||service.waitingForEnd)builder.setNeutralButton("Turn off",(d,w)->{service.cancelTimer();toast("Sleep timer off");});
        AlertDialog dialog=builder.create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int n=0;try{n=Integer.parseInt(minutes.getText().toString());}catch(Exception ignored){}if(n<1||n>1440){minutes.setError("Enter 1 to 1440 minutes");return;}service.setTimer(n,finish.isChecked());dialog.dismiss();toast("Sleep timer started");}));dialog.show();
    }
    private void renderLyrics(){
        LinearLayout tools=horizontal();weight(tools,button("Import",()->{if(service!=null&&service.current()!=null){importTarget=service.current();pick("*/*",41);}else toast("Choose a song first.");}));weight(tools,button("Edit",this::editLyrics));weight(tools,button("Timing",this::timingDialog));content.addView(tools);gap(content,8);
        lyricScroll=new ScrollView(this);lyricsText=text("Loading local lyrics…",17,foreground);lyricsText.setPadding(dp(12),dp(16),dp(12),dp(80));lyricsText.setLineSpacing(dp(10),1f);lyricsText.setTextIsSelectable(true);lyricScroll.addView(lyricsText);content.addView(lyricScroll,new LinearLayout.LayoutParams(-1,0,1));lyricsUri="";loadLyrics();
    }
    private void loadLyrics(){if(lyricsText==null)return;if(service==null||service.current()==null){lyricsText.setText("Choose a song, then import or edit its lyrics.");return;}Song song=service.current();lyricsUri=song.uri;lyricsSource="";lyricLines=new ArrayList<>();lastLyric=-2;lyricsText.setText("Loading local lyrics…");io.execute(()->{String result=MediaFiles.lyrics(this,song,store);main.post(()->{if(dead||lyricsText==null||!song.uri.equals(lyricsUri))return;lyricsSource=result;lyricLines=Lyrics.parse(result);lastLyric=service==null?-1:Lyrics.current(lyricLines,service.position());displayLyrics();});});}
    private void displayLyrics(){if(lyricsText==null)return;if(lyricsSource.trim().isEmpty()){lyricsText.setText("No embedded or imported lyrics found.\n\nImport an LRC or TXT file, or use Edit to add lyrics.");return;}if(lyricLines.isEmpty()){lyricsText.setText(lyricsSource);return;}SpannableStringBuilder out=new SpannableStringBuilder();int activeOffset=-1;for(int i=0;i<lyricLines.size();i++){int start=out.length();out.append(lyricLines.get(i).text).append("\n\n");if(i==lastLyric){activeOffset=start;out.setSpan(new ForegroundColorSpan(accent),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}lyricsText.setText(out);if(activeOffset>=0){final int offset=activeOffset;lyricsText.post(()->{if(dead||lyricsText==null||lyricScroll==null||lyricsText.getLayout()==null)return;int line=lyricsText.getLayout().getLineForOffset(offset);lyricScroll.scrollTo(0,Math.max(0,lyricsText.getLayout().getLineTop(line)-lyricScroll.getHeight()/3));});}}
    private void editLyrics(){if(service==null||service.current()==null)return;Song song=service.current();EditText editor=new EditText(this);editor.setText(lyricsSource);editor.setGravity(Gravity.TOP);editor.setMinLines(10);editor.setTextSize(15);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);LinearLayout fields=vertical();fields.setPadding(dp(16),0,dp(16),0);fields.addView(text("LRC timestamps or plain text",13,muted));fields.addView(button("Stamp current line",()->{int cursor=Math.max(0,editor.getSelectionStart());String value=editor.getText().toString();int start=value.lastIndexOf('\n',Math.max(0,cursor-1))+1;int end=value.indexOf('\n',start);if(end<0)end=value.length();String line=value.substring(start,end).replaceFirst("^(\\[\\d+:\\d+(?:[.:]\\d+)?\\])+","");String stamped=Lyrics.stamp(service.position())+line;editor.getText().replace(start,end,stamped);editor.setSelection(Math.min(editor.length(),start+stamped.length()+1));}));ScrollView scroll=new ScrollView(this);scroll.addView(editor);fields.addView(scroll,new LinearLayout.LayoutParams(-1,dp(270)));new AlertDialog.Builder(this).setTitle("Edit lyrics").setView(fields).setPositiveButton("Save",(d,w)->saveLyrics(song,editor.getText().toString())).setNegativeButton("Cancel",null).show();}
    private void saveLyrics(Song song,String value){io.execute(()->{try{store.write(store.lyricsFile(song),value);main.post(()->{if(!dead){toast("Lyrics saved locally");loadLyrics();}});}catch(Exception e){main.post(()->toast("Could not save lyrics"));}});}
    private void timingDialog(){if(service==null||service.current()==null)return;if(lyricLines.isEmpty()){toast("Add LRC timestamps using Edit first.");return;}Song song=service.current();new AlertDialog.Builder(this).setTitle("Adjust all lyric timestamps").setItems(new String[]{"Earlier by 0.25 seconds","Later by 0.25 seconds","Custom offset in milliseconds"},(d,w)->{if(w<2)saveLyrics(song,Lyrics.shift(lyricsSource,w==0?-250:250));else prompt("Offset in ms, e.g. -500 or 500","0",value->{try{long offset=Long.parseLong(value);if(Math.abs(offset)>3600000)throw new NumberFormatException();saveLyrics(song,Lyrics.shift(lyricsSource,offset));}catch(Exception e){toast("Enter an offset within one hour.");}});}).show();}
    private void renderSettings(){
        ScrollView scroll=new ScrollView(this);LinearLayout body=vertical();scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        setting(body,"Theme",()->new AlertDialog.Builder(this).setTitle("Theme").setItems(new String[]{"System","Light","Dark"},(d,w)->{store.prefs.edit().putString("theme",new String[]{"system","light","dark"}[w]).apply();render();}).show());
        setting(body,"Accent colour",()->new AlertDialog.Builder(this).setTitle("Accent colour").setItems(new String[]{"Crimson","Blue","Teal","Violet","Amber"},(d,w)->{store.prefs.edit().putInt("accent",new int[]{0xFFB7234D,0xFF225BCC,0xFF087A72,0xFF7545B5,0xFF996600}[w]).apply();render();}).show());
        setting(body,"Choose custom background",()->pick("image/*",43));
        if(new File(getFilesDir(),"background.jpg").exists())setting(body,"Remove custom background",()->{new File(getFilesDir(),"background.jpg").delete();render();});
        setting(body,"Library filters",this::filters);setting(body,"Rescan library",this::scan);setting(body,"Check for updates",()->checkUpdates());
        if(Build.VERSION.SDK_INT>=33)setting(body,"Notification permission",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11));
        gap(body,20);TextView about=text("Lunera Lite 1.0.0\nAndroid 6.0+ · GPL-3.0\nMaintained within Grimmoir Organization\n\nPlayback stops when you remove Lite from Recents. Switching apps or locking the screen keeps your music playing.\n\nNo scheduled scans, background update polling, online lyrics or online artwork downloads.",14,muted);body.addView(about);gap(body,12);setting(body,"Source code and releases",()->browse("https://github.com/Grimmoir-Organization/Lunera-Lite"));
    }
    private void setting(LinearLayout body,String label,Runnable action){body.addView(button(label,action),new LinearLayout.LayoutParams(-1,dp(48)));gap(body,8);}
    private void checkUpdates(){toast("Checking Lunera Lite releases…");io.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL("https://api.github.com/repos/Grimmoir-Organization/Lunera-Lite/releases/latest").openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Lunera-Lite/1.0.0");int status=c.getResponseCode();if(status==404){main.post(()->{if(!dead)toast("No public Lunera Lite release is available yet.");});return;}if(status!=200)throw new IOException("HTTP "+status);String body;try(InputStream in=c.getInputStream()){body=Store.readText(in,1024*1024);}JSONObject release=new JSONObject(body);String tag=release.optString("tag_name"),url=release.optString("html_url");boolean newer=newer(tag,"1.0.0");main.post(()->{if(dead||!visible)return;if(newer)new AlertDialog.Builder(this).setTitle("Lunera Lite "+tag).setMessage(release.optString("body","An update is available.")).setPositiveButton("View release",(d,w)->browse(url)).setNegativeButton("Later",null).show();else toast("You have the latest published version.");});}catch(Exception e){main.post(()->{if(!dead&&visible)toast("Could not check for updates. Check your connection and try again.");});}finally{if(c!=null)c.disconnect();}});}
    static boolean newer(String tag,String current){try{String[] a=tag.replaceFirst("^[vV]","").split("\\."),b=current.split("\\.");for(int i=0;i<Math.max(a.length,b.length);i++){int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;if(x!=y)return x>y;}}catch(Exception ignored){}return false;}
    private void browse(String url){if(!url.startsWith("https://github.com/Grimmoir-Organization/Lunera-Lite")){toast("Invalid release link");return;}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("No browser is installed.");}}
    private void pick(String mime,int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){toast("No file picker is installed.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();final Song target=importTarget;
        if(request==41&&target!=null)io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();String value=Store.readText(in,1024*1024);store.write(store.lyricsFile(target),value);main.post(()->{if(!dead){toast("Lyrics imported");loadLyrics();}});}catch(Exception e){main.post(()->toast("Could not import lyrics. Choose a local LRC or TXT file."));}});
        else if(request==42&&target!=null)io.execute(()->{try{MediaFiles.importImage(this,uri,new File(getFilesDir(),store.artFile(target)),768);main.post(()->{if(!dead){artUri="";loadArt();toast("Artwork saved");}});}catch(Exception e){main.post(()->toast("Could not import image"));}});
        else if(request==43)io.execute(()->{try{MediaFiles.importImage(this,uri,new File(getFilesDir(),"background.jpg"),1280);main.post(()->{if(!dead){render();toast("Background saved");}});}catch(Exception e){main.post(()->toast("Could not import background image"));}});
    }
    private String clock(long ms){long seconds=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",seconds/60,seconds%60);}
    private void toast(String message){if(!dead)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
