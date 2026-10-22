package com.example.dex2oat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class IncrementalWorker extends Worker {
 public IncrementalWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){
  if(!Store.auto(getApplicationContext()))return Result.success();
  TaskRuntime.line("自动周期任务触发：开始检查 Root");
  if(!Engine.rootOk()){TaskRuntime.line("自动周期任务：Root 不可用，将由 WorkManager 重试");return Result.retry();}
  org.json.JSONObject record=Engine.runScheduled(getApplicationContext(),TaskRuntime::line);
  if(record==null){TaskRuntime.line("自动周期任务：未能开始或出现异常，将重试");return Result.retry();}
  TaskRuntime.line("自动周期任务记录已保存：已处理 "+record.optInt("processed")+"/"+record.optInt("requested")+"，成功 "+record.optInt("success")+"，失败 "+record.optInt("failed")+"，跳过 "+record.optInt("skipped")+"，未处理 "+record.optInt("pending"));
  return Result.success();
 }
 static void schedule(Context ctx){WorkManager wm=WorkManager.getInstance(ctx);
  if(!Store.auto(ctx)){wm.cancelUniqueWork("incremental_compile");return;}
  PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(IncrementalWorker.class,Math.max(1,Store.interval(ctx)),TimeUnit.HOURS)
    .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build();
  wm.enqueueUniquePeriodicWork("incremental_compile",ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,req);
 }
}
