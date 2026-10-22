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
  // The APK Root engine is the only execution backend.
  if(!Store.claimAutoWindow(ctx)){TaskRuntime.line("自动增量任务：距离上次启动不足 "+Store.interval(ctx)+" 小时，跳过本次重复触发");return Result.success();}
  TaskRuntime.line("自动周期任务触发：间隔 "+Store.interval(ctx)+" 小时，检查 Root");
  if(!Engine.rootOk()){TaskRuntime.line("自动周期任务：Root 不可用，等待下一周期");return Result.success();}
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
