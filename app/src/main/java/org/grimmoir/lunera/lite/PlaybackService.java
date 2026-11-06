package org.grimmoir.lunera.lite;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns audio independently of Activity views; never restarts after task dismissal. */
public final class PlaybackService extends Service {
    public final class LocalBinder extends Binder {public PlaybackService getService(){return PlaybackService.this;}}
    public interface Listener {void changed();}
    private final LocalBinder binder=new LocalBinder();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    public final ArrayList<Song> queue=new ArrayList<>();
    private Store store;
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    public int index=-1,repeat=0;
    public boolean shuffle=false;
    private boolean ready=false,preparing=false,seeking=false,wantPlay=false,resumeOnFocus=false,dismissed=false,foreground=false,destroyed=false;
    private int restorePosition=0;
    public long timerDeadline=0;
    public boolean finishSong=false,waitingForEnd=false;
    public String error="";
    private final Random random=new Random();
    private final AudioManager.OnAudioFocusChangeListener focusListener=change->{
        if(change==AudioManager.AUDIOFOCUS_LOSS){resumeOnFocus=false;pause();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){boolean was=isPlaying()||wantPlay;pauseInternal(false);resumeOnFocus=was;}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(player!=null)player.setVolume(.2f,.2f);}
        else if(change==AudioManager.AUDIOFOCUS_GAIN){if(player!=null)player.setVolume(1f,1f);if(resumeOnFocus){resumeOnFocus=false;play();}}
    };
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){resumeOnFocus=false;pause();}};
    private final Runnable checkpoint=new Runnable(){@Override public void run(){if(destroyed)return;savePosition();if(isPlaying())handler.postDelayed(this,15000);}};
    private final Runnable timerFire=()->{
        PlaybackRules.TimerResult decision=PlaybackRules.timer(SystemClock.elapsedRealtime(),timerDeadline,finishSong);
        if(decision==PlaybackRules.TimerResult.NONE)return;
        timerDeadline=0;
        if(decision==PlaybackRules.TimerResult.FINISH_SONG && (isPlaying()||preparing)){waitingForEnd=true;changed();}
        else{waitingForEnd=false;pause();changed();}
    };
    @Override public void onCreate(){
        super.onCreate();store=new Store(this);audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        queue.addAll(store.loadSongs("queue.json"));index=store.prefs.getInt("queueIndex",0);if(index<0||index>=queue.size())index=queue.isEmpty()?-1:0;
        restorePosition=store.prefs.getInt("position",0);repeat=store.prefs.getInt("repeat",0);shuffle=store.prefs.getBoolean("shuffle",false);
        session=new MediaSession(this,"LuneraLite");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){play();}
            @Override public void onPause(){pause();}
            @Override public void onStop(){stopPlayback();}
            @Override public void onSkipToNext(){next(false);}
            @Override public void onSkipToPrevious(){previous();}
            @Override public void onSeekTo(long pos){seek((int)Math.min(Integer.MAX_VALUE,pos));}
        });
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,filter);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel("playback","Music playback",NotificationManager.IMPORTANCE_LOW);ch.setSound(null,null);ch.enableVibration(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}
        updateSession();
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){String action=intent.getAction();if("toggle".equals(action))toggle();else if("next".equals(action))next(false);else if("previous".equals(action))previous();else if(Intent.ACTION_MEDIA_BUTTON.equals(action)){android.view.KeyEvent key=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);if(key!=null&&key.getAction()==android.view.KeyEvent.ACTION_DOWN){switch(key.getKeyCode()){case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:next(false);break;case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:previous();break;case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:pause();break;case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:play();break;default:toggle();}}}}
        return START_NOT_STICKY;
    }
    public void addListener(Listener l){listeners.addIfAbsent(l);}
    public void removeListener(Listener l){listeners.remove(l);}
    public Song current(){return index>=0&&index<queue.size()?queue.get(index):null;}
    public boolean isPlaying(){return ready&&player!=null&&player.isPlaying();}
    public boolean isPreparing(){return preparing;}
    public int position(){if(seeking)return restorePosition;if(ready&&player!=null)try{return player.getCurrentPosition();}catch(IllegalStateException ignored){}return restorePosition;}
    public int duration(){if(ready&&player!=null)try{return player.getDuration();}catch(IllegalStateException ignored){}Song s=current();return s==null?0:(int)Math.min(Integer.MAX_VALUE,s.duration);}
    public void toggle(){if(isPlaying()||wantPlay)pause();else play();}
    private boolean requestFocus(){
        if(Build.VERSION.SDK_INT>=26){if(focusRequest==null)focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(focusListener).build();return audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
        return audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    private void abandonFocus(){if(Build.VERSION.SDK_INT>=26&&focusRequest!=null)audio.abandonAudioFocusRequest(focusRequest);else audio.abandonAudioFocus(focusListener);}
    public void play(){
        if(destroyed||dismissed||current()==null)return;
        // A bound Activity starts the service while visible; foreground promotion precedes API 35 focus.
        startService(new Intent(this,PlaybackService.class));
        wantPlay=true;session.setActive(true);showNotification(true);
        if(!requestFocus()){wantPlay=false;error="Another app currently has audio focus.";showNotification(false);changed();return;}
        if(ready){player.start();changed();handler.removeCallbacks(checkpoint);handler.postDelayed(checkpoint,15000);}
        else if(!preparing)prepareCurrent();
    }
    private void prepareCurrent(){
        Song song=current();if(song==null)return;
        releasePlayer();error="";preparing=true;
        final MediaPlayer mp=new MediaPlayer();player=mp;
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(p->{
            if(player!=p||destroyed||dismissed)return;
            preparing=false;ready=true;
            if(restorePosition>0){restorePosition=Math.min(restorePosition,Math.max(0,p.getDuration()-1));seeking=true;p.seekTo(restorePosition);}
            if(wantPlay){p.start();handler.removeCallbacks(checkpoint);handler.postDelayed(checkpoint,15000);}
            changed();
        });
        mp.setOnCompletionListener(p->{
            if(player!=p)return;
            if(waitingForEnd){waitingForEnd=false;restorePosition=0;pause();seek(0);}
            else next(true);
        });
        mp.setOnSeekCompleteListener(p->{if(player==p&&!destroyed){seeking=false;savePosition();updateSession();}});
        mp.setOnErrorListener((p,what,extra)->{if(player==p){error="Could not play this file. Check the file and storage permission.";wantPlay=false;releasePlayer();abandonFocus();showNotification(false);changed();}return true;});
        try{mp.setDataSource(this,song.contentUri());mp.prepareAsync();changed();}
        catch(Exception e){error="Cannot open this song. Rescan the library if it was moved.";wantPlay=false;releasePlayer();abandonFocus();showNotification(false);changed();}
    }
    public void pause(){
        pauseInternal(true);
    }
    private void pauseInternal(boolean abandon){
        wantPlay=false;resumeOnFocus=false;
        if(isPlaying())player.pause();savePosition();handler.removeCallbacks(checkpoint);if(abandon)abandonFocus();
        changed();
    }
    public void seek(int position){restorePosition=Math.max(0,position);if(ready&&player!=null){restorePosition=Math.min(restorePosition,duration());seeking=true;player.seekTo(restorePosition);}savePosition();updateSession();}
    public void playList(List<Song> songs,int selected){
        if(songs.isEmpty())return;
        savePosition();queue.clear();queue.addAll(songs);index=Math.max(0,Math.min(selected,queue.size()-1));restorePosition=0;releasePlayer();persistQueue();play();
    }
    public void select(int selected){if(selected<0||selected>=queue.size())return;savePosition();index=selected;restorePosition=0;releasePlayer();persistQueue();play();}
    public void next(boolean completion){
        if(queue.isEmpty())return;
        int next=shuffle && queue.size()>1 && !(completion&&repeat==2)?randomOther():PlaybackRules.next(index,queue.size(),repeat,completion);
        if(next<0){pause();seek(0);return;}select(next);
    }
    private int randomOther(){int n=random.nextInt(queue.size()-1);return n>=index?n+1:n;}
    public void previous(){if(position()>3000)seek(0);else if(!queue.isEmpty())select(index<=0?queue.size()-1:index-1);}
    public void enqueue(Song s){queue.add(s);if(index<0)index=0;persistQueue();changed();}
    public void remove(int item){
        if(item<0||item>=queue.size())return;
        boolean current=item==index;boolean resume=isPlaying()||wantPlay;
        if(current){savePosition();releasePlayer();restorePosition=0;}
        queue.remove(item);if(item<index)index--;else if(index>=queue.size())index=queue.size()-1;
        persistQueue();if(queue.isEmpty())stopPlayback();else if(current&&resume)play();else changed();
    }
    public void move(int from,int to){if(from<0||to<0||from>=queue.size()||to>=queue.size())return;Song active=current();Song s=queue.remove(from);queue.add(to,s);index=queue.indexOf(active);persistQueue();changed();}
    public void modes(){store.prefs.edit().putInt("repeat",repeat).putBoolean("shuffle",shuffle).apply();changed();}
    public void setTimer(int minutes,boolean finish){cancelTimer();finishSong=finish;timerDeadline=SystemClock.elapsedRealtime()+minutes*60000L;handler.postDelayed(timerFire,minutes*60000L);changed();}
    public void cancelTimer(){timerDeadline=0;waitingForEnd=false;finishSong=false;handler.removeCallbacks(timerFire);changed();}
    private void persistQueue(){List<Song> snapshot=new ArrayList<>(queue);storage.execute(()->store.saveSongs("queue.json",snapshot));savePosition();}
    private void savePosition(){if(store!=null)store.prefs.edit().putInt("queueIndex",index).putInt("position",position()).apply();}
    private void releasePlayer(){ready=false;preparing=false;seeking=false;if(player!=null){player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnSeekCompleteListener(null);player.setOnErrorListener(null);player.release();player=null;}}
    private void changed(){if(destroyed)return;updateSession();if(foreground)showNotification(false);for(Listener l:listeners)l.changed();}
    private void updateSession(){
        if(session==null)return;Song song=current();
        if(song!=null)session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,song.title).putString(MediaMetadata.METADATA_KEY_ARTIST,song.artist).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP;
        int state=preparing?PlaybackState.STATE_BUFFERING:isPlaying()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(state,position(),isPlaying()?1f:0f).build());
    }
    private PendingIntent action(String name,int id){return PendingIntent.getService(this,id,new Intent(this,PlaybackService.class).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private void showNotification(boolean promote){
        Song song=current();if(song==null||destroyed||dismissed)return;
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"playback"):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_note).setContentTitle(song.title).setContentText(song.artist).setColor(0xFFB7234D).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(isPlaying()||wantPlay)
          .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
          .addAction(R.drawable.ic_prev,"Previous",action("previous",1)).addAction(isPlaying()||wantPlay?R.drawable.ic_pause:R.drawable.ic_play,isPlaying()||wantPlay?"Pause":"Play",action("toggle",2)).addAction(R.drawable.ic_next,"Next",action("next",3))
          .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2));
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        Notification notification=b.build();
        if(promote){if(Build.VERSION.SDK_INT>=29)startForeground(71,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(71,notification);foreground=true;}
        else if(foreground)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,notification);
    }
    public void stopPlayback(){wantPlay=false;resumeOnFocus=false;restorePosition=position();savePosition();releasePlayer();cancelTimer();handler.removeCallbacks(checkpoint);abandonFocus();if(session!=null)session.setActive(false);stopForeground(true);foreground=false;((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(71);stopSelf();changed();}
    @Override public void onTaskRemoved(Intent rootIntent){dismissed=true;stopPlayback();if(session!=null){session.release();session=null;}stopSelf();super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){
        savePosition();destroyed=true;handler.removeCallbacksAndMessages(null);releasePlayer();abandonFocus();
        try{unregisterReceiver(noisy);}catch(IllegalArgumentException ignored){}
        if(session!=null){session.setActive(false);session.release();session=null;}
        stopForeground(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(71);listeners.clear();storage.shutdown();super.onDestroy();
    }
}
