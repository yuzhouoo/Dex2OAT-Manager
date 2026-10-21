package com.example.dex2oat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.*;
import org.json.*;
import java.util.*;

final class Store {
 static final String[] FILTERS={"speed-profile","speed","verify"};
 private static final String PREF="dex2oat_state_v2";
 static SharedPreferences pref(Context c){return c.getApplicationContext().getSharedPreferences(PREF,0);}
 static String global(Context c){return pref(c).getString("global","speed-profile");}
 static void global(Context c,String m){pref(c).edit().putString("global",m).apply();}
 static int interval(Context c){return pref(c).getInt("interval",24);}
 static void interval(Context c,int h){pref(c).edit().putInt("interval",h).apply();}
 static boolean auto(Context c){return pref(c).getBoolean("auto",false);}
 static void auto(Context c,boolean b){pref(c).edit().putBoolean("auto",b).apply();}
 static boolean includeSystem(Context c){return pref(c).getBoolean("system",false);}
 static void includeSystem(Context c,boolean b){pref(c).edit().putBoolean("system",b).apply();}
 static JSONArray read(Context c,String key){try{return new JSONArray(pref(c).getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
 static void write(Context c,String key,JSONArray data){pref(c).edit().putString(key,data.toString()).commit();}
 static JSONArray groups(Context c){return read(c,"groups");}
 static JSONArray history(Context c){return read(c,"history");}
 static JSONObject marks(Context c){try{return new JSONObject(pref(c).getString("marks","{}"));}catch(Exception e){return new JSONObject();}}
 static void marks(Context c,JSONObject o){pref(c).edit().putString("marks",o.toString()).commit();}
 static boolean validMode(String m){for(String a:FILTERS)if(a.equals(m))return true;return false;}
 static String chosenMode(Context c,String pkg){JSONArray a=groups(c);for(int i=0;i<a.length();i++){
  JSONObject g=a.optJSONObject(i);if(g==null)continue;JSONArray pk=g.optJSONArray("packages");if(pk==null)continue;
  for(int j=0;j<pk.length();j++)if(pkg.equals(pk.optString(j)))return g.optString("mode",global(c));
 }return global(c);}
 static String fingerprint(PackageInfo p){return p.getLongVersionCode()+":"+p.lastUpdateTime;}
 static List<PackageInfo> installed(Context c){return installed(c,includeSystem(c));}
 static List<PackageInfo> installedAll(Context c){return installed(c,true);}
 static List<PackageInfo> installed(Context c,boolean withSystem){List<PackageInfo> list=new ArrayList<>();
  for(PackageInfo p:c.getPackageManager().getInstalledPackages(0)){
   if(p.applicationInfo==null || p.packageName.equals(c.getPackageName()))continue;
   if(!withSystem && ((p.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0))continue;
   list.add(p);
  }
  Collections.sort(list,(a,b)->a.packageName.compareToIgnoreCase(b.packageName));return list;
 }
 static synchronized void record(Context c,JSONObject entry){JSONArray a=history(c),out=new JSONArray();out.put(entry);for(int i=0;i<a.length() && i<99;i++)out.put(a.opt(i));write(c,"history",out);}
 static synchronized void clearHistory(Context c){write(c,"history",new JSONArray());}
 static synchronized void saveGroup(Context c,int editIndex,String name,String mode,Set<String> pkgs)throws Exception{
  if(name.trim().isEmpty()||!validMode(mode)||pkgs.isEmpty())throw new Exception("请填写分组名称、模式并选择应用");
  JSONArray old=groups(c),newGroups=new JSONArray();Set<String> occupied=new HashSet<>();
  for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o==null || i==editIndex)continue;
   JSONArray a=o.optJSONArray("packages");if(a!=null)for(int j=0;j<a.length();j++)occupied.add(a.optString(j));newGroups.put(o);
  }
  for(String p:pkgs)if(occupied.contains(p))throw new Exception("应用已存在其他自定义分组："+p);
  JSONObject g=new JSONObject();g.put("name",name.trim());g.put("mode",mode);JSONArray arr=new JSONArray();List<String> sorted=new ArrayList<>(pkgs);Collections.sort(sorted);for(String p:sorted)arr.put(p);g.put("packages",arr);newGroups.put(g);write(c,"groups",newGroups);
 }
 static synchronized void deleteGroup(Context c,int index){JSONArray a=groups(c),out=new JSONArray();for(int i=0;i<a.length();i++)if(i!=index)out.put(a.opt(i));write(c,"groups",out);}
}
