package com.example.dex2oat;

import android.app.*;
import android.os.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private final ExecutorService pool=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private LinearLayout page,groupsBox,historyBox,appsBox;
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
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(6,9,6,9);return t;}
 private void heading(String title){TextView v=text(title,18);v.setTypeface(null,Typeface.BOLD);page.addView(v);}
 private Button button(String label,LinearLayout p,Runnable action){Button b=new Button(this);b.setText(label);p.addView(b);b.setOnClickListener(v->action.run());return b;}
 private int index(String value){for(int i=0;i<Store.FILTERS.length;i++)if(Store.FILTERS[i].equals(value))return i;return 0;}
 private Spinner modeSpinner(String m){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));s.setSelection(index(m));return s;}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void build(){ScrollView scroll=new ScrollView(this);page=vertical();page.setPadding(22,22,22,32);scroll.addView(page);setContentView(scroll);
  TextView title=text("Dex2OAT 编译管理器",24);title.setTypeface(null,Typeface.BOLD);page.addView(title);
  page.addView(text("Android 16 · KernelSU · ART 编译策略",13));
  root=text("Root：待检测",14);page.addView(root);
  button("检测 KernelSU Root",page,()->pool.execute(()->{boolean ok=Engine.rootOk();ui.post(()->root.setText(ok?"Root：已获得 uid=0":"Root：不可用，请确认 KernelSU 授权"));}));
  heading("编译概览");stats=text("正在统计…",15);page.addView(stats);
  heading("全局编译策略");global=modeSpinner(Store.global(this));page.addView(global);
  button("保存全局编译模式",page,()->{Store.global(this,Store.FILTERS[global.getSelectedItemPosition()]);toast("已保存，全局模式变化将在下次增量扫描时触发重编译");updateStats();});
  heading("自动增量编译");automatic=new Switch(this);automatic.setText("开启定时扫描新安装或更新的 APP");automatic.setChecked(Store.auto(this));page.addView(automatic);
  period=new Spinner(this);String[] intervals=new String[hours.length];for(int i=0;i<hours.length;i++)intervals[i]=hours[i]+" 小时";period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,intervals));for(int i=0;i<hours.length;i++)if(hours[i]==Store.interval(this))period.setSelection(i);page.addView(period);
  page.addView(text("定时检查非精确触发，受省电限制；仅在电量及存储空间充足时运行。模式变化也会重新编译。",12));
  button("保存定时配置",page,()->{Store.auto(this,automatic.isChecked());Store.interval(this,hours[period.getSelectedItemPosition()]);IncrementalWorker.schedule(this);toast("定时设置已保存");});
  button("立即执行增量扫描",page,()->start(true,null));
  heading("自定义 APP 编译分组");page.addView(text("自定义分组优先于全局模式；一个 APP 只能属于一个分组。",12));
  button("新增自定义分组",page,()->editGroup(-1));groupsBox=vertical();page.addView(groupsBox);
  heading("应用列表");systems=new Switch(this);systems.setText("包含系统应用（谨慎操作）");systems.setChecked(Store.includeSystem(this));page.addView(systems);
  systems.setOnCheckedChangeListener((v,b)->{Store.includeSystem(this,b);reload();});
  search=new EditText(this);search.setSingleLine(true);search.setHint("搜索应用或包名");page.addView(search);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){showApps();}public void afterTextChanged(android.text.Editable e){}});
  LinearLayout row=new LinearLayout(this);page.addView(row);button("全选可见",row,()->{for(PackageInfo p:filtered())selected.add(p.packageName);showApps();});button("清空选择",row,()->{selected.clear();showApps();});
  button("编译所选 APP（按各自策略）",page,()->{if(selected.isEmpty()){toast("请选择 APP");return;}List<String> copy=new ArrayList<>(selected);new AlertDialog.Builder(this).setTitle("确认编译").setMessage("将编译 "+copy.size()+" 个应用，优先使用自定义分组模式。建议连接充电器。") .setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,copy)).show();});
  button("全量编译所有可见 APP",page,()->new AlertDialog.Builder(this).setTitle("全量编译确认").setMessage("将对全部当前范围应用执行强制编译，可能耗时较长、发热并占用存储空间。") .setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,null)).show());
  button("停止当前任务",page,Engine::stop);
  button("刷新应用与统计",page,this::reload);
  appsBox=vertical();page.addView(appsBox);
  heading("任务执行记录");button("清空任务记录",page,()->new AlertDialog.Builder(this).setMessage("确认清空历史任务记录？不会清除成功编译标记。") .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{Store.clearHistory(this);showHistory();}).show());
  historyBox=vertical();page.addView(historyBox);
  heading("实时执行日志");log=text("等待任务",12);log.setTextIsSelectable(true);page.addView(log);
 }
 private void reload(){packages=Store.installed(this);showApps();showGroups();showHistory();updateStats();}
 private List<PackageInfo> filtered(){List<PackageInfo> list=new ArrayList<>();if(search==null)return list;String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);PackageManager pm=getPackageManager();for(PackageInfo p:packages){String name=String.valueOf(pm.getApplicationLabel(p.applicationInfo));if(name.toLowerCase(Locale.ROOT).contains(q)||p.packageName.toLowerCase(Locale.ROOT).contains(q))list.add(p);}return list;}
 private void showApps(){if(appsBox==null)return;appsBox.removeAllViews();List<PackageInfo> list=filtered();appsBox.addView(text("可见："+list.size()+" 项，当前已选择 "+selected.size()+" 项",13));PackageManager pm=getPackageManager();for(PackageInfo p:list){CheckBox cb=new CheckBox(this);cb.setText(pm.getApplicationLabel(p.applicationInfo)+" · "+Store.chosenMode(this,p.packageName)+"\n"+p.packageName);cb.setTextSize(12);cb.setChecked(selected.contains(p.packageName));cb.setOnCheckedChangeListener((v,on)->{if(on)selected.add(p.packageName);else selected.remove(p.packageName);});appsBox.addView(cb);}}
 private void updateStats(){if(stats==null)return;JSONObject marks=Store.marks(this);int compiled=0;for(PackageInfo p:packages){String k=Store.fingerprint(p)+"|"+Store.chosenMode(this,p.packageName);if(k.equals(marks.optString(p.packageName)))compiled++;}stats.setText("APP 总数："+packages.size()+"    已成功编译："+compiled+"    待编译/变更："+(packages.size()-compiled));}
 private void showGroups(){if(groupsBox==null)return;groupsBox.removeAllViews();JSONArray a=Store.groups(this);if(a.length()==0)groupsBox.addView(text("暂无自定义分组",13));for(int i=0;i<a.length();i++){final int ix=i;JSONObject g=a.optJSONObject(i);if(g==null)continue;JSONArray pk=g.optJSONArray("packages");groupsBox.addView(text("● "+g.optString("name")+"  ·  "+g.optString("mode")+"  ·  "+(pk==null?0:pk.length())+" 个 APP",14));LinearLayout actions=new LinearLayout(this);groupsBox.addView(actions);button("编辑",actions,()->editGroup(ix));button("删除",actions,()->new AlertDialog.Builder(this).setMessage("删除分组后，这些 APP 将恢复使用全局模式。") .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Store.deleteGroup(this,ix);reload();}).show());}}
 private void editGroup(int ix){JSONObject old=ix<0?null:Store.groups(this).optJSONObject(ix);Set<String> picked=new HashSet<>();if(old!=null){JSONArray p=old.optJSONArray("packages");if(p!=null)for(int j=0;j<p.length();j++)picked.add(p.optString(j));}
  Set<String> unavailable=new HashSet<>();JSONArray gs=Store.groups(this);for(int i=0;i<gs.length();i++)if(i!=ix){JSONArray a=gs.optJSONObject(i).optJSONArray("packages");if(a!=null)for(int j=0;j<a.length();j++)unavailable.add(a.optString(j));}
  LinearLayout layout=vertical();layout.setPadding(20,8,20,6);EditText name=new EditText(this);name.setSingleLine();name.setHint("分组名称，例如：高性能 APP");if(old!=null)name.setText(old.optString("name"));layout.addView(name);Spinner choice=modeSpinner(old==null?"speed":old.optString("mode"));layout.addView(choice);
  EditText filter=new EditText(this);filter.setSingleLine();filter.setHint("过滤 APP");layout.addView(filter);ScrollView sc=new ScrollView(this);LinearLayout items=vertical();sc.addView(items);layout.addView(sc,new LinearLayout.LayoutParams(-1,dp(350)));
  Runnable render=()->{items.removeAllViews();String term=filter.getText().toString().toLowerCase(Locale.ROOT);for(PackageInfo p:packages){String label=String.valueOf(getPackageManager().getApplicationLabel(p.applicationInfo));if(!label.toLowerCase(Locale.ROOT).contains(term)&&!p.packageName.toLowerCase(Locale.ROOT).contains(term))continue;CheckBox c=new CheckBox(this);c.setText(label+"\n"+p.packageName+(unavailable.contains(p.packageName)?"（被其他分组占用）":""));c.setTextSize(12);c.setEnabled(!unavailable.contains(p.packageName));c.setChecked(picked.contains(p.packageName));c.setOnCheckedChangeListener((v,b)->{if(b)picked.add(p.packageName);else picked.remove(p.packageName);});items.addView(c);}};render.run();filter.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(ix<0?"新增分组":"编辑分组").setView(layout).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{Store.saveGroup(this,ix,name.getText().toString(),Store.FILTERS[choice.getSelectedItemPosition()],picked);dialog.dismiss();reload();}catch(Exception e){toast(e.getMessage());}}));dialog.show();
 }
 private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
 private void showHistory(){if(historyBox==null)return;historyBox.removeAllViews();JSONArray h=Store.history(this);if(h.length()==0){historyBox.addView(text("暂无任务记录",13));return;}SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());for(int i=0;i<h.length();i++){JSONObject obj=h.optJSONObject(i);if(obj==null)continue;String label=fmt.format(new Date(obj.optLong("time")))+" · "+obj.optString("type")+"\n成功 "+obj.optInt("success")+" / 失败 "+obj.optInt("failed")+" / 跳过 "+obj.optInt("skipped")+" · "+(obj.optLong("duration")/1000.0)+" 秒"+(obj.optBoolean("cancelled")?" · 已取消":"");button(label,historyBox,()->{JSONArray details=obj.optJSONArray("details");StringBuilder s=new StringBuilder();if(details!=null)for(int j=0;j<details.length();j++){JSONObject x=details.optJSONObject(j);if(x!=null)s.append(x.optBoolean("ok")?"✓ ":"✗ ").append(x.optString("pkg")).append(" (").append(x.optString("mode")).append(")\n").append(x.optString("output")).append("\n\n");}new AlertDialog.Builder(this).setTitle("任务详情").setMessage(s.length()==0?"没有执行项（均被跳过）":s.toString()).setPositiveButton("关闭",null).show();});}}
 private void start(boolean incremental,List<String> targets){if(Engine.busy()){toast("已有任务运行中");return;}log.setText("准备执行…");pool.execute(()->{JSONObject result=Engine.run(this,incremental,targets,this::append);ui.post(()->{reload();if(result==null)toast("任务未启动：已有任务占用或执行异常");});});}
 private void append(String s){ui.post(()->{String text=log.getText().toString()+"\n"+s;if(text.length()>16000)text=text.substring(text.length()-16000);log.setText(text);});}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onDestroy(){pool.shutdown();super.onDestroy();}
}
