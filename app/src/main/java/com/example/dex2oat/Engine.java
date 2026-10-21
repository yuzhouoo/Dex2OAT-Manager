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
 static boolean busy(){return BUSY.get();}
 static void stop(){STOP=true;Process p=ACTIVE;if(p!=null)p.destroy();}
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
 static JSONObject run(Context context,boolean incremental,List<String> targets,Listener listener){
  if(!BUSY.compareAndSet(false,true))return null;
  STOP=false;long started=System.currentTimeMillis();int ok=0,failed=0,skipped=0;JSONArray details=new JSONArray();
  try{
   List<PackageInfo> installed=Store.installed(context);Map<String,PackageInfo> byPkg=new HashMap<>();for(PackageInfo p:installed)byPkg.put(p.packageName,p);
   List<String> pkgs=new ArrayList<>();if(targets==null){pkgs.addAll(byPkg.keySet());}else {for(String p:targets)if(byPkg.containsKey(p))pkgs.add(p);}
   Collections.sort(pkgs);
   JSONObject marks=Store.marks(context);
   for(int i=0;i<pkgs.size();i++){
    if(STOP || (incremental && System.currentTimeMillis()-started>420000L))break;String pkg=pkgs.get(i);PackageInfo p=byPkg.get(pkg);String filter=Store.chosenMode(context,pkg);
    String key=Store.fingerprint(p)+"|"+filter;
    // Mark records include the mode, so changing the group or global mode triggers a new compile.
    if(incremental && key.equals(marks.optString(pkg))){skipped++;continue;}
    if(listener!=null)listener.update("["+(i+1)+"/"+pkgs.size()+"] "+pkg+" · "+filter);
    Result r=root("cmd package compile -m "+filter+" -f "+shellQuote(pkg),240000);
    boolean success=!STOP && compileSuccess(r);if(success){ok++;try{marks.put(pkg,key);Store.marks(context,marks);}catch(Exception ignored){}}else failed++;
    JSONObject detail=new JSONObject();try{detail.put("pkg",pkg);detail.put("mode",filter);detail.put("ok",success);detail.put("code",r.code);detail.put("output",r.out.length()>800?r.out.substring(0,800):r.out);details.put(detail);}catch(Exception ignored){}
    if(listener!=null)listener.update((success?"成功 ":"失败 ")+pkg+"："+r.out.trim());
   }
   JSONObject record=new JSONObject();record.put("time",started);record.put("duration",System.currentTimeMillis()-started);record.put("type",incremental?"增量":"手动");record.put("success",ok);record.put("failed",failed);record.put("skipped",skipped);record.put("cancelled",STOP);record.put("batchLimited",incremental && System.currentTimeMillis()-started>420000L);record.put("details",details);Store.record(context,record);
   if(listener!=null)listener.update("任务结束：成功 "+ok+"，失败 "+failed+"，跳过 "+skipped+"，耗时 "+((System.currentTimeMillis()-started)/1000)+" 秒"+(STOP?"（已中止）":""));
   return record;
  }catch(Exception e){if(listener!=null)listener.update("任务异常："+e);return null;}
  finally{BUSY.set(false);ACTIVE=null;}
 }
}
