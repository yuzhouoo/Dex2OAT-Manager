package com.example.dex2oat;

import android.content.Context;
import android.content.pm.PackageInfo;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class Engine {
 interface Listener {void update(String text);}
 private static final AtomicBoolean BUSY=new AtomicBoolean(false);
 private static volatile boolean STOP=false;
 private static volatile Process ACTIVE;
 private static volatile boolean SCAN=false;
 private static volatile boolean SCAN_STOP=false;
 private static volatile boolean RESERVED=false;
 private static volatile boolean COMPILING=false;
 private static volatile long reservedAt=0L;
 private static final long START_TIMEOUT_MS=30000L;
 static synchronized boolean beginScan(){if(BUSY.get())return false;SCAN_STOP=false;SCAN=true;BUSY.set(true);return true;}
 static synchronized void endScan(){if(SCAN){SCAN=false;SCAN_STOP=false;BUSY.set(false);}}
 static synchronized boolean reserveTask(){recoverPending();if(BUSY.get())return false;STOP=false;RESERVED=true;reservedAt=android.os.SystemClock.elapsedRealtime();BUSY.set(true);return true;}
 static synchronized boolean pending(){recoverPending();return RESERVED;}
 static synchronized boolean claimReserved(){recoverPending();if(!RESERVED||STOP||!BUSY.get())return false;RESERVED=false;COMPILING=true;return true;}
 static synchronized void recoverPending(){if(RESERVED&&android.os.SystemClock.elapsedRealtime()-reservedAt>START_TIMEOUT_MS){RESERVED=false;BUSY.set(false);STOP=false;TaskRuntime.abort("任务启动超时，已释放任务锁");}}
 static synchronized void abandonClaim(){if(!SCAN&&COMPILING){COMPILING=false;BUSY.set(false);STOP=false;TaskRuntime.abort("服务未能启动执行线程，已释放任务锁");}}
 static synchronized boolean cancelPending(){if(!RESERVED)return false;RESERVED=false;BUSY.set(false);STOP=false;TaskRuntime.abort("任务在准备阶段已取消");return true;}
 static boolean scanning(){return SCAN;}
 static boolean compiling(){return COMPILING;}
 static boolean scanCancelled(){return SCAN_STOP;}
 static boolean busy(){return BUSY.get();}
 static synchronized void releaseReserved(){if(!SCAN&&RESERVED){RESERVED=false;BUSY.set(false);}}
 static void stop(){if(cancelPending())return;if(SCAN)SCAN_STOP=true;else STOP=true;Process p=ACTIVE;if(p!=null)p.destroy();}
 static class Result{final int code;final String out;Result(int c,String o){code=c;out=o;}}
 static Result root(String command,long timeout){Process p=null;ByteArrayOutputStream data=new ByteArrayOutputStream();
  try{
   p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();ACTIVE=p;final Process proc=p;
   Thread reader=new Thread(()->{try(InputStream in=proc.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){synchronized(data){if(data.size()<32768)data.write(b,0,Math.min(n,32768-data.size()));}}}catch(Exception ignored){}});
   reader.setDaemon(true);reader.start();boolean ended=p.waitFor(timeout,TimeUnit.MILLISECONDS);
   if(!ended){p.destroyForcibly();reader.join(1500);return new Result(-1,"编译超时");}
   reader.join(1500);return new Result(p.exitValue(),data.toString("UTF-8"));
  }catch(Exception ex){if(p!=null)p.destroyForcibly();return new Result(-1,ex.toString());}
  finally{if(ACTIVE==p)ACTIVE=null;}
 }
 static boolean rootOk(){Result r=root("id",12000);return r.code==0 && r.out.contains("uid=0");}
 static String shellQuote(String s){return "'"+s.replace("'","'\\''")+"'";}
 static boolean compileSuccess(Result r){String s=r.out.trim();return r.code==0 && (s.matches("(?s).*\\bSuccess\\b.*") || s.matches("(?s).*\\bSUCCESS\\b.*")) && !s.matches("(?s).*\\bFailure\\b.*");}
 static JSONObject run(Context context,boolean incremental,List<String> targets,Listener listener){return execute(context,incremental,targets,false,listener,false,0L);}
 static JSONObject runScheduled(Context context,Listener listener){return execute(context,true,null,false,listener,false,420000L,"自动执行","周期增量编译");}
 static JSONObject runReserved(Context context,boolean incremental,List<String> targets,Listener listener){return execute(context,incremental,targets,false,listener,true,0L);}
 static JSONObject runReserved(Context context,boolean incremental,List<String> targets,Listener listener,String scope){return execute(context,incremental,targets,false,listener,true,0L,"手动执行",scope);}
 static JSONObject reset(Context context,List<String> targets,Listener listener){return execute(context,false,targets,true,listener,false,0L);}
 static JSONObject resetReserved(Context context,List<String> targets,Listener listener){return execute(context,false,targets,true,listener,true,0L);}
 static JSONObject resetReserved(Context context,List<String> targets,Listener listener,String scope){return execute(context,false,targets,true,listener,true,0L,"手动执行",scope);}
 private static void addDetail(JSONArray out,String pkg,String mode,String status,int code,String output){
  JSONObject d=new JSONObject();try{d.put("pkg",pkg);d.put("mode",mode);d.put("status",status);d.put("ok","success".equals(status));d.put("code",code);d.put("output",output);out.put(d);}catch(Exception ignored){}
 }
 private static JSONObject execute(Context context,boolean incremental,List<String> targets,boolean reset,Listener listener,boolean reserved,long maxDurationMs){return execute(context,incremental,targets,reset,listener,reserved,maxDurationMs,"手动执行",reset?"重置":"编译应用");}
 private static JSONObject execute(Context context,boolean incremental,List<String> targets,boolean reset,Listener listener,boolean reserved,long maxDurationMs,String origin,String scope){
  if(!reserved){synchronized(Engine.class){if(!BUSY.compareAndSet(false,true))return null;COMPILING=true;}}
  if(!reserved)STOP=false;long started=System.currentTimeMillis();if(!reserved)TaskRuntime.begin("自动执行 · "+scope,0);int ok=0,failed=0,skipped=0,processed=0,missing=0;JSONArray details=new JSONArray();boolean timedOut=false;
  try{
   List<PackageInfo> installed=Store.installedAll(context);Map<String,PackageInfo> byPkg=new HashMap<>();for(PackageInfo p:installed)byPkg.put(p.packageName,p);
   List<String> pkgs=new ArrayList<>();if(targets==null){for(PackageInfo pi:Store.installed(context))pkgs.add(pi.packageName);}else {LinkedHashSet<String> unique=new LinkedHashSet<>(targets);for(String p:unique){if(byPkg.containsKey(p))pkgs.add(p);else {missing++;addDetail(details,p,"","missing",-1,"应用已卸载或不可见：开始前无法找到安装包");}}}
   Collections.sort(pkgs);
   JSONObject marks=Store.marks(context);
   for(int i=0;i<pkgs.size();i++){
    if(STOP || (maxDurationMs>0&&System.currentTimeMillis()-started>=maxDurationMs)){timedOut=!STOP;break;}String pkg=pkgs.get(i);PackageInfo p=byPkg.get(pkg);String filter=Store.chosenMode(context,pkg);
    String key=Store.fingerprint(p)+"|"+filter;
    // Mark records include the mode, so changing the group or global mode triggers a new compile.
    ArtDatabase db=ArtDatabase.get(context);
    ArtState.Entry before=incremental?ArtState.query(pkg):db.getState(pkg,Store.fingerprint(p));
    if(before!=null&&incremental)db.update(context,pkg,before,"incremental-precheck",String.valueOf(started));
    if(incremental){ArtState.Entry st=before;if(ArtState.matches(st,filter)){skipped++;processed++;TaskRuntime.skipped();addDetail(details,pkg,filter,"skipped",-1,"ART 当前已符合目标模式");continue;}
     if(maxDurationMs>0 && key.equals(marks.optString(pkg)) && "speed-profile".equals(filter) && "verify".equals(st.filter)){
      long last=Store.pref(context).getLong("profile_attempt_"+pkg,0L);
      if(System.currentTimeMillis()-last<86400000L){skipped++;processed++;TaskRuntime.skipped();addDetail(details,pkg,filter,"deferred",-1,"Profile 尚未生成或未生效，24 小时后允许再次尝试");if(listener!=null)listener.update("待重试："+pkg+"（缺少 Profile；非已达标）");continue;}
     }
    }
    TaskRuntime.progress(i,pkgs.size(),pkg,filter);
    if(listener!=null)listener.update("["+(i+1)+"/"+pkgs.size()+"] "+pkg+" · "+filter);
    Result r=root(reset?"cmd package compile --reset "+shellQuote(pkg):"cmd package compile -m "+filter+" -f "+shellQuote(pkg),240000);
    boolean success=!STOP && compileSuccess(r);
    ArtState.Entry after=ArtState.query(pkg);
    db.update(context,pkg,after,reset?"reset-verification":"compile-verification",String.valueOf(started));
    boolean verified=ArtState.matches(after,filter);
    processed++;TaskRuntime.outcome(success);
    if(listener!=null)listener.update("ART 验证 "+pkg+"：当前 "+after.filter+"，目标 "+filter+"，"+(verified?"已达标":after.known?"未达标":"未知"));
    if(success){ok++;try{if(reset){marks.remove(pkg);}else{marks.put(pkg,key);if("speed-profile".equals(filter))Store.pref(context).edit().putLong("profile_attempt_"+pkg,System.currentTimeMillis()).apply();}Store.marks(context,marks);}catch(Exception ignored){}}else failed++;
    JSONObject detail=new JSONObject();try{detail.put("pkg",pkg);detail.put("mode",filter);detail.put("ok",success);detail.put("status",success?"success":"failed");detail.put("beforeArt",before==null?"未知":before.filter);detail.put("afterArt",after.filter);detail.put("artMatch",verified);detail.put("artKnown",after.known);detail.put("verifiedAt",System.currentTimeMillis());detail.put("command","cmd package compile "+(reset?"--reset":"-m "+filter+" -f")+" "+pkg);detail.put("code",r.code);detail.put("output",r.out.length()>800?r.out.substring(0,800):r.out);details.put(detail);}catch(Exception ignored){}
    if(listener!=null)listener.update((success?"成功 ":"失败 ")+pkg+"："+("everything".equals(filter)&&!success?"设备可能不支持 everything；请改用 speed。":"")+r.out.trim());
   }
   for(int j=processed;j<pkgs.size();j++){String pkg=pkgs.get(j);addDetail(details,pkg,Store.chosenMode(context,pkg),STOP?"cancelled":"pending",-1,STOP?"用户请求停止":timedOut?"自动周期运行时间上限，留待下次":"任务提前结束");}
   int pending=pkgs.size()-processed;
   JSONObject record=new JSONObject();record.put("origin",origin);record.put("scope",scope);record.put("source","手动执行".equals(origin)?"应用交互":"WorkManager 周期调度");record.put("destination","本地任务历史记录");record.put("requested",pkgs.size()+missing);record.put("processed",processed);record.put("pending",pending);record.put("missing",missing);record.put("time",started);record.put("duration",System.currentTimeMillis()-started);record.put("type",reset?"重置":incremental?"增量":"手动");record.put("verification","per-package");record.put("success",ok);record.put("failed",failed);record.put("skipped",skipped);record.put("cancelled",STOP);record.put("batchLimited",timedOut);record.put("details",details);Store.record(context,record);
   if(listener!=null)listener.update("任务结束：总计 "+(pkgs.size()+missing)+"，已处理 "+processed+"，成功 "+ok+"，失败 "+failed+"，跳过 "+skipped+"，未处理 "+pending+"，不可见 "+missing+"，耗时 "+((System.currentTimeMillis()-started)/1000)+" 秒"+(STOP?"（已中止）":""));
   return record;
  }catch(Exception e){if(listener!=null)listener.update("任务异常："+e);return null;}
  finally{if(!reserved)TaskRuntime.finish("自动任务结束");synchronized(Engine.class){COMPILING=false;BUSY.set(false);}ACTIVE=null;}
 }
}
