package com.example.dex2oat;

import java.util.*;
import java.util.regex.*;

/** Main APK / split APK statuses are authoritative. Secondary DEX is diagnostic only. */
final class ArtState {
 static final class Entry {
  final String pkg,filter,detail;
  final boolean known,mixed;
  final int secondaryCount,secondaryKnown;
  final String secondarySummary;
  Entry(String p,String f,String d,boolean k,boolean m){this(p,f,d,k,m,0,0,"");}
  Entry(String p,String f,String d,boolean k,boolean m,int count,int knownSecondary,String summary){
   pkg=p;filter=f;detail=d;known=k;mixed=m;secondaryCount=count;secondaryKnown=knownSecondary;secondarySummary=summary;
  }
 }
 private static final Pattern STATUS=Pattern.compile("\\[status=([^\\] ]+)",Pattern.CASE_INSENSITIVE);
 static Entry parse(String pkg,String output){
  Set<String> primary=new LinkedHashSet<>();
  Map<String,Integer> secondary=new LinkedHashMap<>();
  boolean inSecondary=false;
  boolean removed=false;
  int secondaryCount=0,secondaryKnown=0;
  for(String line:output.split("\\r?\\n")){
   String trim=line.trim();
   if(trim.equals("known secondary dex files:")){inSecondary=true;continue;}
   if(inSecondary && trim.startsWith("/")){removed=trim.contains("(removed)");secondaryCount++;continue;}
   Matcher found=STATUS.matcher(line);
   if(!found.find())continue;
   String status=found.group(1).toLowerCase(Locale.ROOT);
   if(inSecondary){
    if(!removed && !"unknown".equals(status)){secondaryKnown++;secondary.put(status,secondary.getOrDefault(status,0)+1);}
   }else if(!"unknown".equals(status))primary.add(status);
  }
  boolean mixed=primary.size()>1;
  String mode=primary.isEmpty()?"未知":mixed?"混合":primary.iterator().next();
  StringBuilder summary=new StringBuilder();
  for(Map.Entry<String,Integer> x:secondary.entrySet()){if(summary.length()>0)summary.append("、");summary.append(x.getKey()).append(" ×").append(x.getValue());}
  return new Entry(pkg,mode,output,!primary.isEmpty(),mixed,secondaryCount,secondaryKnown,summary.toString());
 }
 static Entry query(String pkg){
  Engine.Result r=Engine.root("cmd package art dump "+Engine.shellQuote(pkg),0);
  if(r.code!=0 || r.out.trim().isEmpty())r=Engine.root("pm art dump "+Engine.shellQuote(pkg),0);
  if(r.code!=0)return new Entry(pkg,"未知","ART 查询失败："+r.out,false,false);
  return parse(pkg,r.out);
 }
 static boolean matches(Entry st,String target){return st!=null && st.known && !st.mixed && target.equals(st.filter);}
}
