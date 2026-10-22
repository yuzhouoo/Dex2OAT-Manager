package com.example.dex2oat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class IncrementalWorker extends Worker {
 private static final String UNIQUE="incremental_compile";
 public IncrementalWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){
  Context ctx=getApplicationContext();
  if(!Store.auto(ctx))return Result.success();
  if(!Store.autoUsers(ctx)&&!Store.autoSystems(ctx)){TaskRuntime.line("自动增量任务：未选择应用类型，跳过");return Result.success();}
  // WorkManager is sole periodic scheduler; when backend enabled, enqueue instead of executing inside APK.
  // Enforce configured minimum spacing for periodic triggers.
  if(BackendClient.enabled(ctx)&&!BackendClient.available()){TaskRuntime.line("后台离线：保留周期窗口，不使用旧引擎执行");return Result.retry();}
  if(!Store.claimAutoWindow(ctx)){TaskRuntime.line("自动增量任务：距离上次启动不足 "+Store.interval(ctx)+" 小时，跳过本次重复触发");return Result.success();}
  TaskRuntime.line("自动周期任务触发：间隔 "+Store.interval(ctx)+" 小时，检查 Root");
  if(!Engine.rootOk()){TaskRuntime.line("自动周期任务：Root 不可用，等待下一周期");return Result.success();}
  if(BackendClient.enabled(ctx)){
   if(!BackendClient.available()){
    TaskRuntime.line("自动任务：KernelSU 后台不可用，安全跳过，不启动第二套 Root 引擎");
    return Result.retry();
   }
   java.util.Map<String,java.util.List<String>> buckets=new java.util.LinkedHashMap<>();
   for(android.content.pm.PackageInfo pi:Store.installedAll(ctx)){
    int flags=pi.applicationInfo==null?0:pi.applicationInfo.flags;
    boolean system=(flags&(android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
    if((system&&Store.autoSystems(ctx))||(!system&&Store.autoUsers(ctx))){
     String mode=Store.chosenMode(ctx,pi.packageName);
     if(Store.validMode(mode))buckets.computeIfAbsent(mode,k->new java.util.ArrayList<>()).add(pi.packageName);
    }
   }
   boolean allQueued=true;
   for(java.util.Map.Entry<String,java.util.List<String>> e:buckets.entrySet()){
    java.util.List<String> targets=e.getValue();
    for(int offset=0;offset<targets.size();offset+=200){
     String result=BackendClient.submit("incremental",e.getKey(),targets.subList(offset,Math.min(offset+200,targets.size())));
     TaskRuntime.line("KernelSU 自动任务 "+e.getKey()+"："+result);
     if(!result.startsWith("queued:"))allQueued=false;
    }
   }
   return allQueued?Result.success():Result.retry();
  }
  org.json.JSONObject record=Engine.runScheduled(ctx,TaskRuntime::line);
  if(record==null){TaskRuntime.line("自动周期任务：引擎忙或本次执行失败，等待下一周期");return Result.success();}
  TaskRuntime.line("自动周期任务记录已保存：已处理 "+record.optInt("processed")+"/"+record.optInt("requested")+"，实际编译 "+record.optInt("attempted")+"，成功 "+record.optInt("success")+"，失败 "+record.optInt("failed")+"，跳过 "+record.optInt("skipped")+"，未处理 "+record.optInt("pending"));
  return Result.success();
 }
 static synchronized void schedule(Context ctx){
  WorkManager wm=WorkManager.getInstance(ctx);
  if(!Store.auto(ctx)){wm.cancelUniqueWork(UNIQUE);Store.pref(ctx).edit().remove("auto_schedule_signature").apply();return;}
  String signature=Store.interval(ctx)+"|"+Store.autoUsers(ctx)+"|"+Store.autoSystems(ctx);
  boolean changed=!signature.equals(Store.pref(ctx).getString("auto_schedule_signature",""));
  PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(IncrementalWorker.class,Math.max(1,Store.interval(ctx)),TimeUnit.HOURS)
   .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build();
  wm.enqueueUniquePeriodicWork(UNIQUE,changed?ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE:ExistingPeriodicWorkPolicy.KEEP,req);
  if(changed)Store.pref(ctx).edit().putString("auto_schedule_signature",signature).apply();
 }
}
