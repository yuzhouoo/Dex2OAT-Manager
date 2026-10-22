package com.example.dex2oat;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** A user-started foreground service for long manual compilation/reset work. */
public final class CompileService extends Service {
 static final String ACTION_RUN="com.example.dex2oat.RUN";
 static final String ACTION_STOP="com.example.dex2oat.STOP";
 static final String EXTRA_PACKAGES="packages", EXTRA_RESET="reset", EXTRA_INCREMENTAL="incremental", EXTRA_SCOPE="scope",EXTRA_MODE="override_mode";
 private static final int ID=4106;
 private static final String CHANNEL="dex2oat_task";
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private NotificationManager notifications;
 private long lastUpdate;

 @Override public void onCreate(){super.onCreate();notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Dex2OAT 任务进度",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  if(ACTION_STOP.equals(intent.getAction())){Engine.stop();TaskRuntime.line("收到停止请求");postProgress(true);return START_NOT_STICKY;}
  if(!ACTION_RUN.equals(intent.getAction()))return START_NOT_STICKY;
  final ArrayList<String> pkgs=intent.getStringArrayListExtra(EXTRA_PACKAGES);
  final boolean reset=intent.getBooleanExtra(EXTRA_RESET,false);
  final boolean incremental=intent.getBooleanExtra(EXTRA_INCREMENTAL,false);
  final String scope=intent.getStringExtra(EXTRA_SCOPE)==null?"选中应用":intent.getStringExtra(EXTRA_SCOPE);
  final String overrideMode=intent.getStringExtra(EXTRA_MODE);
  if(pkgs==null){Engine.releaseReserved();stopSelf(startId);return START_NOT_STICKY;}
  final boolean backend=BackendClient.enabled(this);
  try{
   startForeground(ID,notification(false),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
  }catch(RuntimeException ex){
   TaskRuntime.abort("前台服务启动失败："+ex.getClass().getSimpleName());
   Engine.releaseReserved();stopSelf(startId);return START_NOT_STICKY;
  }
  if(backend){
   if(!Engine.claimReserved()){
    TaskRuntime.abort("任务已取消或超时");
    stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);
    return START_NOT_STICKY;
   }
   TaskRuntime.begin("正在提交 KernelSU 后台任务",pkgs.size());
   postProgress(true);
   try{
    executor.execute(()->{
     try{
      String mode=overrideMode!=null?overrideMode:Store.global(getApplicationContext());
      String type=reset?"reset":incremental?"incremental":"compile";
      String result=BackendClient.submit(type,mode,pkgs);
      boolean accepted=result.startsWith("queued:");
      TaskRuntime.finish(accepted?"已提交 KernelSU 后台任务："+result:"提交失败："+result);
     }catch(Throwable ex){
      TaskRuntime.finish("后台提交异常："+ex.getClass().getSimpleName());
     }finally{
      Engine.finishClaimedBackend();
      postProgress(true);
      stopForeground(STOP_FOREGROUND_REMOVE);
      stopSelf(startId);
     }
    });
   }catch(RuntimeException ex){
    TaskRuntime.abort("后台提交调度失败："+ex.getClass().getSimpleName());
    Engine.finishClaimedBackend();
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf(startId);
   }
   return START_NOT_STICKY;
  }
  if(!Engine.claimReserved()){TaskRuntime.abort("启动请求已取消或超时");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return START_NOT_STICKY;}
  TaskRuntime.begin(reset?"正在重置编译":incremental?"正在增量编译":"正在编译应用",pkgs.size());postProgress(true);
  try{executor.execute(()->{
   try{
    JSONObject record=reset?Engine.resetReserved(getApplicationContext(),pkgs,this::onLog,scope):Engine.runReserved(getApplicationContext(),incremental,pkgs,this::onLog,scope,overrideMode);
    String end=record==null?"任务异常":("已处理 "+record.optInt("processed")+"/"+record.optInt("requested")+" · 成功 "+record.optInt("success")+" · 失败 "+record.optInt("failed")+" · 跳过 "+record.optInt("skipped")+" · 未处理 "+record.optInt("pending"));
    TaskRuntime.finish(end);
   }catch(Throwable ex){TaskRuntime.finish("执行失败："+ex.getClass().getSimpleName());}
   finally{postProgress(true);stopForeground(STOP_FOREGROUND_DETACH);stopSelf(startId);}
  });}catch(RuntimeException ex){TaskRuntime.abort("任务调度失败");Engine.abandonClaim();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}
  return START_NOT_STICKY;
 }
 private void onLog(String s){TaskRuntime.line(s);postProgress(false);}
 private void postProgress(boolean force){long now=SystemClock.elapsedRealtime();if(!force&&now-lastUpdate<1200)return;lastUpdate=now;notifications.notify(ID,notification(force&&!TaskRuntime.snapshot().running));}
 private Notification notification(boolean complete){
  TaskRuntime.Snapshot s=TaskRuntime.snapshot();
  Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_logs",true);
  PendingIntent openPI=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(s.title).setContentText(s.current).setContentIntent(openPI).setOnlyAlertOnce(true).setOngoing(s.running).setVisibility(Notification.VISIBILITY_PUBLIC);
  if(s.running){
   b.setProgress(Math.max(1,s.total),Math.min(s.total,s.done),s.total<=0);
   Intent stop=new Intent(this,CompileService.class).setAction(ACTION_STOP);
   PendingIntent stopPI=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   b.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"停止任务",stopPI).build());
  }else{b.setProgress(0,0,false).setAutoCancel(true);}
  return b.build();
 }
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){executor.shutdown();super.onDestroy();}
}