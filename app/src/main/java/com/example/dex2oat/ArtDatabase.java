package com.example.dex2oat;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Per-package ART state. One row per package, short transactions, legacy migration. */
final class ArtDatabase extends SQLiteOpenHelper {
 private static final String NAME="art_state_v3.db";
 private static ArtDatabase instance;
 private static volatile long revision=0L;
 static long revision(){return revision;}
 static synchronized ArtDatabase get(Context c){if(instance==null)instance=new ArtDatabase(c.getApplicationContext());return instance;}
 private ArtDatabase(Context c){super(c,NAME,null,2);}
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS art_state (pkg TEXT PRIMARY KEY,mode TEXT NOT NULL,known INTEGER NOT NULL,mixed INTEGER NOT NULL,app_version TEXT,observed_at INTEGER NOT NULL,source TEXT NOT NULL,task_id TEXT,secondary_count INTEGER DEFAULT 0,secondary_summary TEXT DEFAULT '')");}
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
  if(oldVersion<2){db.execSQL("ALTER TABLE art_state ADD COLUMN secondary_count INTEGER DEFAULT 0");db.execSQL("ALTER TABLE art_state ADD COLUMN secondary_summary TEXT DEFAULT ''");}
 }
 synchronized void upsert(String pkg,ArtState.Entry state,String version,String source,String taskId){
  ContentValues v=new ContentValues();v.put("pkg",pkg);v.put("mode",state.filter);v.put("known",state.known?1:0);v.put("mixed",state.mixed?1:0);v.put("app_version",version);v.put("observed_at",System.currentTimeMillis());v.put("secondary_count",state.secondaryCount);v.put("secondary_summary",state.secondarySummary);v.put("source",source);v.put("task_id",taskId);
  getWritableDatabase().insertWithOnConflict("art_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);revision++;
 }
 synchronized ArtState.Entry getState(String pkg,String currentVersion){
  try(Cursor c=getReadableDatabase().query("art_state",new String[]{"mode","known","mixed","app_version","secondary_count","secondary_summary"},"pkg=?",new String[]{pkg},null,null,null)){
   if(!c.moveToFirst())return null;
   String recorded=c.getString(3);
   if(currentVersion!=null&&recorded!=null&&!currentVersion.equals(recorded))return new ArtState.Entry(pkg,"未知","应用版本发生变化，请重新读取 ART",false,false);
   if("混合".equals(c.getString(0)))return new ArtState.Entry(pkg,"未知","旧版混合缓存需重新扫描主 DEX",false,false);
   return new ArtState.Entry(pkg,c.getString(0),"SQLite 主 DEX 缓存",c.getInt(1)==1,c.getInt(2)==1,c.getInt(4),0,c.getString(5));
  }
 }
 synchronized Map<String,ArtState.Entry> snapshot(Context context){
  Map<String,String> versions=new HashMap<>();
  for(android.content.pm.PackageInfo p:Store.installedAll(context))versions.put(p.packageName,Store.fingerprint(p));
  Map<String,ArtState.Entry> result=new HashMap<>();
  for(Map.Entry<String,String> v:versions.entrySet()){ArtState.Entry x=getState(v.getKey(),v.getValue());if(x!=null)result.put(v.getKey(),x);}
  return result;
 }
 synchronized void importLegacy(Context context){
  if(Store.pref(context).getBoolean("art_db_migrated",false))return;
  Map<String,ArtState.Entry> old=Store.loadArt(context);
  Map<String,String> versions=new HashMap<>();
  for(android.content.pm.PackageInfo p:Store.installedAll(context))versions.put(p.packageName,Store.fingerprint(p));
  for(Map.Entry<String,ArtState.Entry> p:old.entrySet())upsert(p.getKey(),p.getValue(),versions.get(p.getKey()),"legacy",null);
  Store.pref(context).edit().putBoolean("art_db_migrated",true).commit();
 }
 static String version(Context ctx,String pkg){try{android.content.pm.PackageInfo p=ctx.getPackageManager().getPackageInfo(pkg,0);return Store.fingerprint(p);}catch(Exception e){return null;}}
 void update(Context ctx,String pkg,ArtState.Entry entry,String source,String taskId){upsert(pkg,entry,version(ctx,pkg),source,taskId);}
}