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
 static boolean available(){Engine.Result r=Engine.root(Engine.shellQuote(CONTROL)+" ping",0);return r.code==0 && r.out.contains("backend-v2");}
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
 private BackendClient(){}
}
