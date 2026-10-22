package com.example.dex2oat;

import android.content.Context;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Optional KernelSU daemon bridge; commands are fixed and package/mode validated. */
final class BackendClient {
 private static final String CONTROL="/data/adb/modules/dex2oat_backend/bin/control";
 private static final Pattern PACKAGE=Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
 static boolean enabled(Context ctx){return Store.pref(ctx).getBoolean("backend_enabled",false);}
 static void enabled(Context ctx,boolean value){Store.pref(ctx).edit().putBoolean("backend_enabled",value).apply();}
 static boolean available(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" health",0);return r.code==0&&r.out.contains("online version=v2.0.0-beta6.1");}
 static boolean resume(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" resume",0);return r.code==0&&r.out.contains("resumed:");}
 static String version(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" version",0);return r.code==0?r.out.trim():"offline";}
 static String status(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" status",0);return r.code==0?r.out.trim():"backend-offline";}
 static String logs(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" logs",0);return r.code==0?r.out:"";}
 static boolean stop(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" stop",0);return r.code==0&&r.out.contains("stop-requested");}
 static String submit(String type,String mode,List<String> packages){
  if(!("compile".equals(type)||"incremental".equals(type)||"reset".equals(type)))return "invalid-type";
  if(!"reset".equals(type)&&!Store.validMode(mode))return "invalid-mode";
  if(packages==null||packages.isEmpty()||packages.size()>2000)return "invalid-count";
  StringBuilder command=new StringBuilder(Engine.shellQuote(CONTROL)).append(" start ").append(type).append(" ").append("reset".equals(type)?"verify":mode);
  for(String pkg:packages){if(pkg==null||!PACKAGE.matcher(pkg).matches())return "invalid-package";command.append(" ").append(Engine.shellQuote(pkg));}
  Engine.Result result=Engine.root(command.toString(),0);
  return result.code==0?result.out.trim():"submit-failed: "+result.out.trim();
 }

 static java.util.Map<String,String> parseStatus(String status){
  java.util.Map<String,String> map=new java.util.HashMap<>();
  if(status==null)return map;
  for(String token:status.split(" ")){int p=token.indexOf('=');if(p>0)map.put(token.substring(0,p),token.substring(p+1));}
  return map;
 }
 static int number(java.util.Map<String,String> map,String key){try{return Math.max(0,Integer.parseInt(map.getOrDefault(key,"0")));}catch(Exception ex){return 0;}}
 static synchronized void importHistory(Context ctx,String logs){
  if(logs==null||logs.isEmpty())return;
  // Import only completed task summaries, de-duplicated across app restarts.
  for(String line:logs.split("\\r?\\n")){
   int pos=line.indexOf(".job finished processed=");
   if(pos<0)continue;
   String[] head=line.substring(0,pos).split(" ");
   if(head.length<3)continue;
   String job=head[head.length-1]+".job";
   String key="backend_history_"+job;
   if(Store.pref(ctx).getBoolean(key,false))continue;
   java.util.Map<String,String> fields=parseStatus(line.substring(pos+".job finished ".length()));
   try{
    org.json.JSONObject record=new org.json.JSONObject();
    record.put("origin","KernelSU 后台");
    record.put("scope","后台持久化任务");
    record.put("source","KernelSU");
    record.put("taskId",job);
    record.put("requested",number(fields,"processed"));
    record.put("processed",number(fields,"processed"));
    record.put("success",number(fields,"ok"));
    record.put("failed",number(fields,"failed")+number(fields,"unmatched"));
    record.put("unmatched",number(fields,"unmatched"));
    record.put("skipped",number(fields,"skipped"));
    record.put("pending",0);
    record.put("finishedAt",System.currentTimeMillis());
    if(Store.pref(ctx).edit().putBoolean(key,true).commit())Store.record(ctx,record);
   }catch(Exception ignored){}
  }
 }
 private BackendClient(){}
}
