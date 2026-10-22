package com.example.dex2oat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Shared, bounded, process-local task view. No Activity references. */
final class TaskRuntime {
 static final class Snapshot {
  final String title, current; final int done,total,ok,failed; final boolean running;
  Snapshot(String title,String current,int done,int total,int ok,int failed,boolean running){
   this.title=title;this.current=current;this.done=done;this.total=total;this.ok=ok;this.failed=failed;this.running=running;
  }
 }
 private static final int LIMIT=250;
 private static final ArrayDeque<String> messages=new ArrayDeque<>();
 private static String title="当前空闲",current="";
 private static int done,total,ok,failed;
 private static boolean running;
 private static long revision=0;
 private TaskRuntime(){}
 static synchronized void begin(String name,int count){title=name;current="准备执行";done=0;total=count;ok=0;failed=0;running=true;line(name+"：共 "+count+" 个应用");}
 static synchronized void progress(int index,int count,String pkg,String mode){done=Math.max(0,index);total=Math.max(0,count);current=pkg+" · "+mode;}
 static synchronized void skipped(){done++;}
 static synchronized void outcome(boolean success){done++;if(success)ok++;else failed++;}
 static synchronized void finish(String summary){running=false;title="任务已结束";current=summary;line(summary);}
 static synchronized Snapshot snapshot(){return new Snapshot(title,current,done,total,ok,failed,running);}
 static synchronized void line(String message){if(message==null)return;String v=message.length()>350?message.substring(0,350):message;messages.addFirst(new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date())+"  "+v);while(messages.size()>LIMIT)messages.removeLast();revision++;}
 static synchronized long revision(){return revision;}
 static synchronized void abort(String reason){if(!running){title="当前空闲";current=reason;}else finish(reason);}
 static synchronized void clearLogs(){messages.clear();revision++;}
 static synchronized List<String> logs(){return new ArrayList<>(messages);}
}