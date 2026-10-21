package com.example.dex2oat;

import android.content.Context;
import java.util.*;
import java.util.regex.*;

/** A conservative parser: unknown output is never treated as a successful compilation. */
final class ArtState {
 static final class Entry {
  final String pkg; final String filter; final String detail; final boolean known; final boolean mixed;
  Entry(String p,String f,String d,boolean k,boolean m){pkg=p;filter=f;detail=d;known=k;mixed=m;}
 }
 private static final Pattern FILTER=Pattern.compile("(?i)(?:\\bfilter\\s*[=:]\\s*|\\b(?:compiler-filter|compiler filter)\\s*[=:]?\\s*|\\[status=\\s*)(speed-profile|speed|verify|quicken|extract|assume-verified|run-from-apk(?:-fallback)?)\\b");
 static Entry query(String pkg){
  Engine.Result r=Engine.root("cmd package art dump "+Engine.shellQuote(pkg),20000);
  if(r.code!=0 || r.out.trim().isEmpty())r=Engine.root("pm art dump "+Engine.shellQuote(pkg),20000);
  if(r.code!=0)return new Entry(pkg,"未知","ART 查询失败："+r.out,false,false);
  Matcher m=FILTER.matcher(r.out);LinkedHashSet<String> found=new LinkedHashSet<>();while(m.find())found.add(m.group(1).toLowerCase(Locale.ROOT));
  if(found.isEmpty())return new Entry(pkg,"未知",r.out,false,false);
  boolean mixed=found.size()>1;String mode=mixed?"混合":found.iterator().next();
  return new Entry(pkg,mode,r.out,true,mixed);
 }
 static boolean matches(Entry state,String target){
  // Profile may be missing. Do not claim it matches or retry forever simply because it is verify.
  return state.known && !state.mixed && state.filter.equals(target);
 }
}
