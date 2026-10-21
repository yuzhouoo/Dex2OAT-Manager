package com.example.dex2oat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class IncrementalWorker extends Worker {
 public IncrementalWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){
  if(!Store.auto(getApplicationContext()))return Result.success();
  if(!Engine.rootOk())return Result.retry();
  org.json.JSONObject record=Engine.runScheduled(getApplicationContext(),null);
  if(record==null)return Result.retry();
  return Result.success();
 }
 static void schedule(Context ctx){WorkManager wm=WorkManager.getInstance(ctx);
  if(!Store.auto(ctx)){wm.cancelUniqueWork("incremental_compile");return;}
  PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(IncrementalWorker.class,Math.max(1,Store.interval(ctx)),TimeUnit.HOURS)
    .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build();
  wm.enqueueUniquePeriodicWork("incremental_compile",ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,req);
 }
}
