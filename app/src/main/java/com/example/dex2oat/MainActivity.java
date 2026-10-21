package com.example.dex2oat;

import android.app.*;
import android.os.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private final ExecutorService pool=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private LinearLayout page,groupsBox,historyBox,appsBox,navBar;
 private LinearLayout[] screens=new LinearLayout[4];
 private TextView[] navLabels=new TextView[4];
 private int activeTab=0;
 private static final int BG=0xffF5F7FB, INK=0xff17243B, MUTED=0xff6D7B91, ACCENT=0xff425BD8, BORDER=0xffE4E9F2;
 private TextView stats,log,root;
 private Spinner global,period;
 private Switch automatic,systems;
 private EditText search;
 private final Set<String> selected=new HashSet<>();
 private List<PackageInfo> packages=new ArrayList<>();
 private final String[] modes={"speed-profile（推荐）","speed（完整 AOT）","verify（校验）"};
 private final int[] hours={1,3,6,12,24,48,72,168};
 private boolean loading=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);build();reload();IncrementalWorker.schedule(this);}
 private GradientDrawable shape(int color,int stroke,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setPadding(dp(3),dp(5),dp(3),dp(5));return t;}
 private void heading(String title){TextView v=text(title,17);v.setTextColor(INK);v.setTypeface(null,Typeface.BOLD);v.setPadding(0,dp(15),0,dp(10));page.addView(v);}
 private Button button(String label,LinearLayout p,Runnable action){Button b=new Button(this);b.setText(label);b.setTextAllCaps(false);b.setTextSize(13);b.setTextColor(ACCENT);b.setBackground(shape(0xffF0F3FF,BORDER,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.setMargins(0,dp(4),0,dp(4));p.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
 private void primary(String label,LinearLayout parent,Runnable action){Button b=button(label,parent,action);b.setTextColor(Color.WHITE);b.setBackground(shape(ACCENT,0,12));}
 private int index(String value){for(int i=0;i<Store.FILTERS.length;i++)if(Store.FILTERS[i].equals(value))return i;return 0;}
 private Spinner modeSpinner(String m){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));s.setSelection(index(m));s.setMinimumHeight(dp(48));return s;}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout card(String subtitle){LinearLayout box=vertical();box.setPadding(dp(16),dp(12),dp(16),dp(14));box.setBackground(shape(Color.WHITE,BORDER,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));page.addView(box,lp);if(subtitle!=null){TextView title=text(subtitle,15);title.setTypeface(null,Typeface.BOLD);box.addView(title);}return box;}
 private void switchPage(int tab){activeTab=tab;for(int i=0;i<screens.length;i++){((View)screens[i].getTag()).setVisibility(i==tab?View.VISIBLE:View.GONE);navLabels[i].setTextColor(i==tab?ACCENT:MUTED);navLabels[i].setTypeface(null,i==tab?Typeface.BOLD:Typeface.NORMAL);navLabels[i].setBackground(shape(i==tab?0xffEDF0FF:Color.WHITE,0,12));}}
 private void build(){
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout shell=vertical();shell.setBackgroundColor(BG);setContentView(shell);
  LinearLayout header=vertical();header.setPadding(dp(20),dp(13),dp(20),dp(10));header.setBackgroundColor(Color.WHITE);shell.addView(header);
  TextView title=text("Dex2OAT",23);title.setTypeface(null,Typeface.BOLD);header.addView(title);
  TextView subtitle=text("ART 编译管理 · Android 16 / KernelSU",12);subtitle.setTextColor(MUTED);header.addView(subtitle);
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(12),dp(7),dp(12),dp(7));tabs.setBackgroundColor(Color.WHITE);shell.addView(tabs);
  String[] names={"概览","应用","策略","记录"};String[] icons={"◈","▦","⚙","◷"};
  for(int i=0;i<4;i++){final int tab=i;TextView label=text(icons[i]+" "+names[i],13);label.setGravity(Gravity.CENTER);label.setPadding(dp(2),dp(11),dp(2),dp(11));tabs.addView(label,new LinearLayout.LayoutParams(0,-2,1));navLabels[i]=label;label.setOnClickListener(v->switchPage(tab));}
  for(int i=0;i<4;i++){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=vertical();page.setPadding(dp(18),dp(17),dp(18),dp(28));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));screens[i]=page;page.setTag(scroll);}
  // Each content view is attached to its own scrolling host.
  page=screens[0];heading("设备状态");LinearLayout statusCard=card(null);
  root=text("Root 状态 · 待检测",14);root.setTextColor(MUTED);statusCard.addView(root);
  button("检测 KernelSU 授权",statusCard,()->pool.execute(()->{boolean ok=Engine.rootOk();ui.post(()->{root.setText(ok?"● Root 已授权 · uid=0":"● Root 不可用 · 请在 KernelSU 中授权");root.setTextColor(ok?0xff188269:0xffC85A3C);});}));
  heading("编译概览");LinearLayout statCard=card(null);stats=text("正在统计…",16);stats.setTypeface(null,Typeface.BOLD);statCard.addView(stats);
  TextView hint=text("成功数按当前 APK 版本及编译策略统计",12);hint.setTextColor(MUTED);statCard.addView(hint);
  heading("快捷操作");LinearLayout quick=card(null);primary("扫描新增 / 更新应用",quick,()->start(true,null));button("开始编译全部应用",quick,()->new AlertDialog.Builder(this).setTitle("全量编译确认").setMessage("将对当前范围全部应用执行强制编译，可能发热并占用存储空间。建议充电时执行。").setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,null)).show());button("刷新状态",quick,this::reload);
  TextView foot=text("建议优先使用 speed-profile；系统后台调度时间可能延迟。",12);foot.setTextColor(MUTED);page.addView(foot);
  page=screens[1];heading("应用管理");LinearLayout manage=card("选择需要编译的应用");
  systems=new Switch(this);systems.setText("包含系统应用");systems.setTextSize(13);systems.setChecked(Store.includeSystem(this));manage.addView(systems);systems.setOnCheckedChangeListener((v,b)->{Store.includeSystem(this,b);reload();});
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索应用名称 / 包名");manage.addView(search);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){showApps();}public void afterTextChanged(android.text.Editable e){}});
  LinearLayout selections=vertical();manage.addView(selections);button("全选筛选结果",selections,()->{for(PackageInfo p:filtered())selected.add(p.packageName);showApps();});button("清空选择",selections,()->{selected.clear();showApps();});
  primary("编译已选择的应用",manage,()->{if(selected.isEmpty()){toast("请先选择应用");return;}List<String> copy=new ArrayList<>(selected);new AlertDialog.Builder(this).setTitle("确认编译").setMessage("本次编译 "+copy.size()+" 个应用。自定义分组的模式优先于全局模式。") .setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,copy)).show();});
  heading("应用列表");appsBox=card(null);
  page=screens[2];heading("默认编译策略");LinearLayout globalCard=card("全局编译模式");global=modeSpinner(Store.global(this));globalCard.addView(global);TextView gnote=text("未加入自定义分组的应用，使用此模式。",12);gnote.setTextColor(MUTED);globalCard.addView(gnote);primary("保存默认模式",globalCard,()->{Store.global(this,Store.FILTERS[global.getSelectedItemPosition()]);toast("全局模式已保存");updateStats();});
  heading("自动增量编译");LinearLayout autoCard=card(null);automatic=new Switch(this);automatic.setText("开启周期扫描");automatic.setTextSize(14);automatic.setChecked(Store.auto(this));autoCard.addView(automatic);
  period=new Spinner(this);String[] intervals=new String[hours.length];for(int i=0;i<hours.length;i++)intervals[i]=hours[i]+" 小时";period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,intervals));for(int i=0;i<hours.length;i++)if(hours[i]==Store.interval(this))period.setSelection(i);autoCard.addView(period);
  TextView note=text("检测新增、更新或编译策略变化的 APP。执行时间由 Android 调度，可能延迟。",12);note.setTextColor(MUTED);autoCard.addView(note);button("保存自动编译设置",autoCard,()->{Store.auto(this,automatic.isChecked());Store.interval(this,hours[period.getSelectedItemPosition()]);IncrementalWorker.schedule(this);toast("定时设置已保存");});
  heading("自定义编译分组");TextView sub=text("一个应用只能归属一个分组，分组配置优先于全局模式。",12);sub.setTextColor(MUTED);page.addView(sub);LinearLayout groupActions=card(null);primary("＋ 新增自定义分组",groupActions,()->editGroup(-1));groupsBox=card(null);
  page=screens[3];heading("任务执行记录");LinearLayout historyActions=card(null);button("清空执行历史",historyActions,()->new AlertDialog.Builder(this).setMessage("清空任务记录？成功编译标记将保留。") .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{Store.clearHistory(this);showHistory();}).show());historyBox=card(null);
  heading("实时日志");LinearLayout logCard=card(null);log=text("暂无正在执行的任务",12);log.setTextIsSelectable(true);log.setTextColor(MUTED);logCard.addView(log);button("停止当前任务",logCard,Engine::stop);
  switchPage(0);
 }
 private void reload(){packages=Store.installed(this);showApps();showGroups();showHistory();updateStats();}
 private List<PackageInfo> filtered(){List<PackageInfo> list=new ArrayList<>();if(search==null)return list;String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);PackageManager pm=getPackageManager();for(PackageInfo p:packages){String name=String.valueOf(pm.getApplicationLabel(p.applicationInfo));if(name.toLowerCase(Locale.ROOT).contains(q)||p.packageName.toLowerCase(Locale.ROOT).contains(q))list.add(p);}return list;}
 private void showApps(){if(appsBox==null)return;appsBox.removeAllViews();List<PackageInfo> list=filtered();appsBox.addView(text("匹配 " +list.size()+" 个应用 · 已选 " +selected.size()+" 个",13));PackageManager pm=getPackageManager();for(PackageInfo p:list){CheckBox cb=new CheckBox(this);cb.setText(pm.getApplicationLabel(p.applicationInfo)+" · "+Store.chosenMode(this,p.packageName)+"\n"+p.packageName);cb.setTextSize(12);cb.setChecked(selected.contains(p.packageName));cb.setOnCheckedChangeListener((v,on)->{if(on)selected.add(p.packageName);else selected.remove(p.packageName);});appsBox.addView(cb);}}
 private void updateStats(){if(stats==null)return;JSONObject marks=Store.marks(this);int compiled=0;for(PackageInfo p:packages){String k=Store.fingerprint(p)+"|"+Store.chosenMode(this,p.packageName);if(k.equals(marks.optString(p.packageName)))compiled++;}stats.setText("应用总数  "+packages.size()+"\n已编译  "+compiled+"     待处理  "+(packages.size()-compiled));}
 private void showGroups(){if(groupsBox==null)return;groupsBox.removeAllViews();JSONArray a=Store.groups(this);if(a.length()==0)groupsBox.addView(text("暂无自定义分组",13));for(int i=0;i<a.length();i++){final int ix=i;JSONObject g=a.optJSONObject(i);if(g==null)continue;JSONArray pk=g.optJSONArray("packages");groupsBox.addView(text("● "+g.optString("name")+"  ·  "+g.optString("mode")+"  ·  "+(pk==null?0:pk.length())+" 个 APP",14));LinearLayout actions=vertical();groupsBox.addView(actions);button("编辑",actions,()->editGroup(ix));button("删除",actions,()->new AlertDialog.Builder(this).setMessage("删除分组后，这些 APP 将恢复使用全局模式。") .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Store.deleteGroup(this,ix);reload();}).show());}}
 private void editGroup(int ix){JSONObject old=ix<0?null:Store.groups(this).optJSONObject(ix);Set<String> picked=new HashSet<>();if(old!=null){JSONArray p=old.optJSONArray("packages");if(p!=null)for(int j=0;j<p.length();j++)picked.add(p.optString(j));}
  Set<String> unavailable=new HashSet<>();JSONArray gs=Store.groups(this);for(int i=0;i<gs.length();i++)if(i!=ix){JSONArray a=gs.optJSONObject(i).optJSONArray("packages");if(a!=null)for(int j=0;j<a.length();j++)unavailable.add(a.optString(j));}
  LinearLayout layout=vertical();layout.setPadding(20,8,20,6);EditText name=new EditText(this);name.setSingleLine();name.setHint("分组名称，例如：高性能 APP");if(old!=null)name.setText(old.optString("name"));layout.addView(name);Spinner choice=modeSpinner(old==null?"speed":old.optString("mode"));layout.addView(choice);
  EditText filter=new EditText(this);filter.setSingleLine();filter.setHint("过滤 APP");layout.addView(filter);ScrollView sc=new ScrollView(this);LinearLayout items=vertical();sc.addView(items);layout.addView(sc,new LinearLayout.LayoutParams(-1,dp(350)));
  Runnable render=()->{items.removeAllViews();String term=filter.getText().toString().toLowerCase(Locale.ROOT);for(PackageInfo p:packages){String label=String.valueOf(getPackageManager().getApplicationLabel(p.applicationInfo));if(!label.toLowerCase(Locale.ROOT).contains(term)&&!p.packageName.toLowerCase(Locale.ROOT).contains(term))continue;CheckBox c=new CheckBox(this);c.setText(label+"\n"+p.packageName+(unavailable.contains(p.packageName)?"（被其他分组占用）":""));c.setTextSize(12);c.setEnabled(!unavailable.contains(p.packageName));c.setChecked(picked.contains(p.packageName));c.setOnCheckedChangeListener((v,b)->{if(b)picked.add(p.packageName);else picked.remove(p.packageName);});items.addView(c);}};render.run();filter.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(ix<0?"新增分组":"编辑分组").setView(layout).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{Store.saveGroup(this,ix,name.getText().toString(),Store.FILTERS[choice.getSelectedItemPosition()],picked);dialog.dismiss();reload();}catch(Exception e){toast(e.getMessage());}}));dialog.show();
 }
 private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
 private void showHistory(){if(historyBox==null)return;historyBox.removeAllViews();JSONArray h=Store.history(this);if(h.length()==0){historyBox.addView(text("暂无任务记录",13));return;}SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());for(int i=0;i<h.length();i++){JSONObject obj=h.optJSONObject(i);if(obj==null)continue;String label=fmt.format(new Date(obj.optLong("time")))+" · "+obj.optString("type")+"\n成功 "+obj.optInt("success")+" / 失败 "+obj.optInt("failed")+" / 跳过 "+obj.optInt("skipped")+" · "+(obj.optLong("duration")/1000.0)+" 秒"+(obj.optBoolean("cancelled")?" · 已取消":"");button(label,historyBox,()->{JSONArray details=obj.optJSONArray("details");StringBuilder s=new StringBuilder();if(details!=null)for(int j=0;j<details.length();j++){JSONObject x=details.optJSONObject(j);if(x!=null)s.append(x.optBoolean("ok")?"✓ ":"✗ ").append(x.optString("pkg")).append(" (").append(x.optString("mode")).append(")\n").append(x.optString("output")).append("\n\n");}new AlertDialog.Builder(this).setTitle("任务详情").setMessage(s.length()==0?"没有执行项（均被跳过）":s.toString()).setPositiveButton("关闭",null).show();});}}
 private void start(boolean incremental,List<String> targets){if(Engine.busy()){toast("已有任务运行中");return;}log.setText("准备执行…");switchPage(3);pool.execute(()->{JSONObject result=Engine.run(this,incremental,targets,this::append);ui.post(()->{reload();switchPage(3);if(result==null)toast("任务未启动：已有任务占用或执行异常");});});}
 private void append(String s){ui.post(()->{String text=log.getText().toString()+"\n"+s;if(text.length()>16000)text=text.substring(text.length()-16000);log.setText(text);});}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onDestroy(){pool.shutdown();super.onDestroy();}
}
