package com.example.dex2oat;

import android.app.*;
import android.content.Intent;
import android.Manifest;
import android.os.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private final ExecutorService pool=Executors.newSingleThreadExecutor();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private LinearLayout page,navBar;
 private RecyclerView appsList,groupsList,historyList;
 private CheckBox classSelect; private TextView classCounter;
 private int userScroll=0, systemScroll=0; private Boolean renderedSystemTab=null;
 private LinearLayout[] screens=new LinearLayout[5];
 private TextView[] navLabels=new TextView[5];
 private int activeTab=0;
 private static final int BG=0xffF5F7FB, INK=0xff17243B, MUTED=0xff6D7B91, ACCENT=0xff425BD8, BORDER=0xffE4E9F2;
 private TextView stats,root, globalTaskLabel;
 private RecyclerView logsList; private long logRevision=-1; private List<String> visibleLogs=new ArrayList<>(); private boolean followLogs=true;
 private ProgressBar globalProgress;
 private LinearLayout statsGrid, appListRows;
 private volatile int scanDone=0,scanTotal=0;
 private volatile String phase="";
 private Spinner global,period;
 private Switch automatic;
 private TextView taskStatus;
 private Button stopTaskButton,userAppTab,systemAppTab;
 private boolean systemTab=false;
 private final Handler taskHandler=new Handler(Looper.getMainLooper());
 private boolean watchedServiceTask=false;
 private final Runnable taskPulse=new Runnable(){public void run(){refreshLogs();TaskRuntime.Snapshot t=TaskRuntime.snapshot();if(t.running)watchedServiceTask=true;else if(watchedServiceTask&&!Engine.busy()){watchedServiceTask=false;showHistory();pool.execute(()->{JSONObject h=Store.history(getApplicationContext()).optJSONObject(0);refreshTaskPackages(h);});}updateTaskState();taskHandler.postDelayed(this,1200);}};
 
 private final Set<String> selected=new HashSet<>();
 private List<PackageInfo> packages=new ArrayList<>();
 private final String[] modes={"speed-profile（推荐）","speed（完整 AOT）","verify（校验）","everything（实验性，可能不支持）"};
 private final int[] hours={1,3,6,12,24,48,72,168};
 private boolean loading=false;
 private final Map<String,ArtState.Entry> artStates=new ConcurrentHashMap<>();
 private volatile boolean scanning=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);build();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},201);reload();IncrementalWorker.schedule(this);refreshRoot();scanArt();}
 private AlertDialog.Builder styledDialog(){return new AlertDialog.Builder(this,android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);}
 private GradientDrawable shape(int color,int stroke,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setPadding(dp(3),dp(5),dp(3),dp(5));return t;}
 private void heading(String title){TextView v=text(title,17);v.setTextColor(INK);v.setTypeface(null,Typeface.BOLD);v.setPadding(0,dp(15),0,dp(10));page.addView(v);}
 private Button makeButton(String label,Runnable action,boolean prominent){Button b=new Button(this);b.setText(label);b.setTransformationMethod(null);b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);b.setTextColor(prominent?Color.WHITE:ACCENT);b.setBackground(clickShape(prominent?ACCENT:0xffEEF2FF, prominent?0:0xffDEE5FD,12));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(6),0,dp(6),0);b.setElevation(dp(1));b.setOnClickListener(v->{b.animate().scaleX(.96f).scaleY(.96f).setDuration(65).withEndAction(()->b.animate().scaleX(1f).scaleY(1f).setDuration(110).start()).start();action.run();});return b;}
 private android.graphics.drawable.Drawable clickShape(int bg,int stroke,int rad){android.graphics.drawable.RippleDrawable ripple=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33425BD8),shape(bg,stroke,rad),null);return ripple;}
 private Button button(String label,LinearLayout p,Runnable action){Button b=makeButton(label,action,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(4),0,dp(4));p.addView(b,lp);return b;}
 private void doubleButtons(LinearLayout parent,String a,Runnable actionA,String b,Runnable actionB){LinearLayout row=new LinearLayout(this);row.setOrientation(0);parent.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));Button left=makeButton(a,actionA,false);Button right=makeButton(b,actionB,true);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(44),1);l.setMargins(0,0,dp(5),0);row.addView(left,l);LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(44),1);r.setMargins(dp(5),0,0,0);row.addView(right,r);}
 private TextView metric(String value,String label,int color){LinearLayout wrapper=vertical();wrapper.setGravity(Gravity.CENTER);wrapper.setPadding(dp(2),dp(10),dp(2),dp(10));TextView v=text(value,20);v.setTypeface(null,Typeface.BOLD);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(0,0,0,0);wrapper.addView(v);TextView t=text(label,11);t.setTextColor(MUTED);t.setGravity(Gravity.CENTER);wrapper.addView(t);return v;}
 private void renderMetrics(int total,int done,int pending,int unknown){if(statsGrid==null)return;statsGrid.removeAllViews();String[] values={""+total,""+done,""+pending,""+unknown};String[] names={"总数","已完成","待处理","未知"};int[] cs={INK,0xff16866F,0xffD48227,0xff8693A9};for(int i=0;i<4;i++){LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(1),dp(11),dp(1),dp(11));cell.setBackground(shape(i%2==0?0xffF6F8FF:0xffFAFBFF,0,12));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,-2,1);if(i<3)cl.setMargins(0,0,dp(5),0);statsGrid.addView(cell,cl);TextView val=text(values[i],20);val.setTextColor(cs[i]);val.setTypeface(null,Typeface.BOLD);val.setGravity(Gravity.CENTER);cell.addView(val);TextView lab=smallLabel(names[i]);lab.setGravity(Gravity.CENTER);cell.addView(lab);final int category=i;cell.setBackground(clickShape(i%2==0?0xffF6F8FF:0xffFAFBFF,0,12));cell.setClickable(true);cell.setOnClickListener(v->showMetricDetails(category));}}
 private void taskBlocked(){toast("正在"+(Engine.scanning()?"获取 ART 状态":"执行编译任务")+"，请完成后再操作");}
 private boolean blocked(){if(Engine.busy()){taskBlocked();return true;}return false;}
 private TextView smallLabel(String value){TextView t=text(value,12);t.setTextColor(MUTED);return t;}
 private void divider(LinearLayout parent){View v=new View(this);v.setBackgroundColor(BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(8),0,dp(8));parent.addView(v,lp);}
 private void primary(String label,LinearLayout parent,Runnable action){Button b=makeButton(label,action,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(4),0,dp(4));parent.addView(b,lp);}
 private int index(String value){for(int i=0;i<Store.FILTERS.length;i++)if(Store.FILTERS[i].equals(value))return i;return 0;}
 private Spinner modeSpinner(String m){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));s.setSelection(index(m));s.setMinimumHeight(dp(48));return s;}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout card(String subtitle){LinearLayout box=vertical();box.setPadding(dp(16),dp(12),dp(16),dp(14));box.setBackground(shape(Color.WHITE,BORDER,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));page.addView(box,lp);if(subtitle!=null){TextView title=text(subtitle,15);title.setTypeface(null,Typeface.BOLD);box.addView(title);}return box;}
 private void switchPage(int tab){activeTab=tab;for(int i=0;i<screens.length;i++){((View)screens[i].getTag()).setVisibility(i==tab?View.VISIBLE:View.GONE);navLabels[i].setTextColor(i==tab?ACCENT:MUTED);navLabels[i].setTypeface(null,i==tab?Typeface.BOLD:Typeface.NORMAL);navLabels[i].setBackground(shape(i==tab?0xffEDF0FF:Color.WHITE,0,12));}}
 private void build(){
  getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout shell=vertical();shell.setBackgroundColor(BG);
  if(android.os.Build.VERSION.SDK_INT>=35){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getAttributes();getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());shell.setPadding(0,bars.top,0,bars.bottom);return insets;});}
  setContentView(shell);
  LinearLayout header=vertical();header.setPadding(dp(20),dp(13),dp(20),dp(10));header.setBackgroundColor(Color.WHITE);shell.addView(header);
  LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(titleRow);TextView title=text("Dex2OAT",23);title.setTypeface(null,Typeface.BOLD);titleRow.addView(title);root=text("（Root 检测中）",12);root.setTextColor(MUTED);titleRow.addView(root);
  LinearLayout progressShell=vertical();progressShell.setPadding(dp(19),dp(7),dp(19),dp(8));progressShell.setBackground(shape(0xffEEF2FF,0,0));shell.addView(progressShell);globalTaskLabel=text("✓ 当前空闲",12);globalTaskLabel.setTextColor(0xff16866F);progressShell.addView(globalTaskLabel);globalProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);globalProgress.setMax(100);globalProgress.setVisibility(View.GONE);progressShell.addView(globalProgress,new LinearLayout.LayoutParams(-1,dp(4)));
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(12),dp(7),dp(12),dp(7));tabs.setBackgroundColor(Color.WHITE);shell.addView(tabs);
  String[] names={"概览","应用","策略","记录","日志"};String[] icons={"◈","▦","⚙","◷","≡"};
  for(int i=0;i<5;i++){final int tab=i;TextView label=text(icons[i]+" "+names[i],13);label.setGravity(Gravity.CENTER);label.setPadding(dp(2),dp(11),dp(2),dp(11));tabs.addView(label,new LinearLayout.LayoutParams(0,-2,1));navLabels[i]=label;label.setOnClickListener(v->switchPage(tab));}
  for(int i=0;i<5;i++){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=vertical();page.setPadding(dp(18),dp(17),dp(18),dp(28));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));screens[i]=page;page.setTag(scroll);}
  // Each content view is attached to its own scrolling host.
  page=screens[0];
  
  LinearLayout statCard=card(null);statCard.setPadding(dp(18),dp(16),dp(18),dp(17));statsGrid=new LinearLayout(this);statCard.addView(statsGrid,new LinearLayout.LayoutParams(-1,-2));stats=text("正在统计…",12);stats.setTextColor(MUTED);statCard.addView(stats);
  TextView hint=smallLabel("实际 ART 达标与历史成功分开统计 · 未知状态不视为失败");statCard.addView(hint);
  heading("当前任务");LinearLayout running=card(null);taskStatus=text("● 空闲 · 无正在执行的任务",14);running.addView(taskStatus);stopTaskButton=makeButton("停止当前任务",()->{if(Engine.busy()){Engine.stop();toast("已发送停止请求，将中止后续操作");updateTaskState();}else toast("当前没有运行中的任务");},false);running.addView(stopTaskButton,new LinearLayout.LayoutParams(-1,dp(42)));updateTaskState();
  heading("快捷操作");LinearLayout quick=card(null);doubleButtons(quick,"编译全部应用",()->{if(blocked())return;styledDialog().setTitle("全量编译确认").setMessage("强制编译全部目标应用可能造成发热及存储占用，建议充电时执行。") .setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,null)).show();},"刷新全局 ART",()->confirmArtScan());
  TextView foot=smallLabel("推荐 speed-profile；后台周期任务由 Android 调度，执行时间可能延迟。");page.addView(foot);
  page=screens[1];LinearLayout manage=card(null);
  doubleButtons(manage,"扫描增量应用",()->confirmCompile(true,null),"编译选中应用",()->{if(selected.isEmpty()){toast("请先选择应用");return;}List<String> copy=new ArrayList<>(selected);styledDialog().setTitle("确认编译").setMessage("本次编译 "+copy.size()+" 个应用；自定义分组优先于全局模式。").setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(false,copy)).show();});
  Button resetSelection=makeButton("重置选中应用",()->confirmReset(new ArrayList<>(selected)),false);manage.addView(resetSelection,new LinearLayout.LayoutParams(-1,dp(44)));
  LinearLayout appTabs=new LinearLayout(this);LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(-1,dp(48));tabParams.setMargins(0,dp(14),0,dp(8));appTabs.setPadding(dp(3),dp(3),dp(3),dp(3));appTabs.setBackground(shape(0xffEEF2FF,0,13));manage.addView(appTabs,tabParams);
  userAppTab=makeButton("用户应用",()->{saveListScroll();systemTab=false;showApps();},true);systemAppTab=makeButton("系统应用",()->{saveListScroll();systemTab=true;showApps();},false);
  LinearLayout.LayoutParams userTabLp=new LinearLayout.LayoutParams(0,-1,1);userTabLp.setMargins(0,0,dp(1),0);appTabs.addView(userAppTab,userTabLp);LinearLayout.LayoutParams systemTabLp=new LinearLayout.LayoutParams(0,-1,1);systemTabLp.setMargins(dp(1),0,0,0);appTabs.addView(systemAppTab,systemTabLp);
  LinearLayout selectLine=new LinearLayout(this);selectLine.setGravity(Gravity.CENTER_VERTICAL);manage.addView(selectLine,new LinearLayout.LayoutParams(-1,dp(43)));
  classSelect=new CheckBox(this);classSelect.setButtonTintList(ColorStateList.valueOf(ACCENT));selectLine.addView(classSelect);classCounter=smallLabel("");selectLine.addView(classCounter,new LinearLayout.LayoutParams(0,-2,1));
  classSelect.setOnClickListener(v->{boolean on=classSelect.isChecked();for(PackageInfo a:filtered()){if(on)selected.add(a.packageName);else selected.remove(a.packageName);}showApps();});
  appsList=createList(manage,440);
  page=screens[2];LinearLayout globalCard=card("全局默认编译模式");LinearLayout modeLine=new LinearLayout(this);modeLine.setGravity(Gravity.CENTER_VERTICAL);globalCard.addView(modeLine,new LinearLayout.LayoutParams(-1,dp(48)));global=modeSpinner(Store.global(this));modeLine.addView(global,new LinearLayout.LayoutParams(0,dp(48),1));Button saveGlobal=makeButton("保存",()->{String mode=Store.FILTERS[global.getSelectedItemPosition()];Store.global(this,mode);toast("全局模式已保存"+("everything".equals(mode)?"；设备支持情况待验证":""));updateStats();showGroups();},true);LinearLayout.LayoutParams gbutton=new LinearLayout.LayoutParams(dp(88),dp(42));gbutton.setMargins(dp(10),0,0,0);modeLine.addView(saveGlobal,gbutton);globalCard.addView(smallLabel("未加入自定义分组的应用使用全局模式；everything 为实验性选项，Android 16 可能拒绝。"));
  LinearLayout autoCard=card(null);automatic=new Switch(this);automatic.setText("开启自动周期扫描");automatic.setTextSize(14);automatic.setChecked(Store.auto(this));autoCard.addView(automatic);
  LinearLayout periodLine=new LinearLayout(this);periodLine.setGravity(Gravity.CENTER_VERTICAL);autoCard.addView(periodLine,new LinearLayout.LayoutParams(-1,dp(48)));period=new Spinner(this);String[] intervals=new String[hours.length];for(int i=0;i<hours.length;i++)intervals[i]=hours[i]+" 小时";period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,intervals));for(int i=0;i<hours.length;i++)if(hours[i]==Store.interval(this))period.setSelection(i);periodLine.addView(period,new LinearLayout.LayoutParams(0,dp(48),1));Button savePeriod=makeButton("保存",()->{Store.auto(this,automatic.isChecked());Store.interval(this,hours[period.getSelectedItemPosition()]);IncrementalWorker.schedule(this);toast("增量编译设置已保存");},true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(88),dp(42));ap.setMargins(dp(10),0,0,0);periodLine.addView(savePeriod,ap);autoCard.addView(smallLabel("自动检测新增、更新与模式变化；调度时间可能延迟。"));
  LinearLayout groupHeading=new LinearLayout(this);groupHeading.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);page.addView(groupHeading,new LinearLayout.LayoutParams(-1,dp(54)));Button addGroup=makeButton("＋ 新建编译分组",()->editGroup(-1),true);LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(dp(150),dp(40));groupHeading.addView(addGroup,addLp);
  TextView sub=smallLabel("分组策略优先于全局模式，每个应用只能加入一个分组。");page.addView(sub);
  LinearLayout groupHost=card(null);groupsList=createList(groupHost,300);
  page=screens[3];LinearLayout historyActions=card(null);button("清空执行历史",historyActions,()->styledDialog().setMessage("清空任务记录？成功编译标记将保留。") .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{Store.clearHistory(this);showHistory();}).show());LinearLayout historyCard=card(null);historyList=createList(historyCard,260);
  page=screens[4];page.setPadding(dp(14),dp(12),dp(14),dp(12));
  LinearLayout logsCard=card(null);logsCard.setPadding(dp(9),dp(10),dp(9),dp(10));logsList=createList(logsCard,520);logsList.setAdapter(new RecyclerView.Adapter<RowHolder>(){public RowHolder onCreateViewHolder(android.view.ViewGroup parent,int type){android.widget.FrameLayout host=new android.widget.FrameLayout(MainActivity.this);host.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RowHolder(host);}public void onBindViewHolder(RowHolder holder,int position){holder.container.removeAllViews();TextView item=smallLabel(visibleLogs.get(position));item.setTextSize(12);item.setTextIsSelectable(true);item.setPadding(dp(8),dp(6),dp(8),dp(6));holder.container.addView(item,new android.widget.FrameLayout.LayoutParams(-1,-2));}public int getItemCount(){return visibleLogs.size();}});logsList.addOnScrollListener(new RecyclerView.OnScrollListener(){
   @Override public void onScrolled(RecyclerView rv,int dx,int dy){if(dy>0)followLogs=false;else if(!rv.canScrollVertically(-1))followLogs=true;}
  });
  refreshLogs();
  switchPage(getIntent().getBooleanExtra("open_logs",false)?4:0);taskHandler.post(taskPulse);
 }
 private void reload(){packages=Store.installedAll(this);showApps();showGroups();showHistory();updateStats();}
 @Override protected void onResume(){super.onResume();updateTaskState();refreshLogs();showHistory();if(root!=null)refreshRoot();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_logs",false))switchPage(4);}
 private void refreshRoot(){if(Engine.busy())return;pool.execute(()->{if(Engine.busy())return;boolean ok=Engine.rootOk();ui.post(()->{root.setText(ok?"（Root 已授权）":"（Root 未授权）");root.setTextColor(ok?0xff188269:0xffC85A3C);});});}
 private void scanArt(){if(!Engine.beginScan()){taskBlocked();return;}scanning=true;scanDone=0;scanTotal=packages.size();phase="正在获取 ART 状态";updateTaskState();pool.execute(()->{try{if(!Engine.rootOk())return;for(PackageInfo p:new ArrayList<>(packages)){if(Engine.scanCancelled())break;artStates.put(p.packageName,ArtState.query(p.packageName));scanDone++;if(scanDone%8==0)ui.post(()->{if(activeTab==2)showGroups();updateStats();updateTaskState();});}}finally{scanning=false;Engine.endScan();phase="";ui.post(()->{showApps();showGroups();updateStats();updateTaskState();});}}); }
 private void refreshTaskPackages(JSONObject record){
  if(record==null)return;
  JSONArray rows=record.optJSONArray("details");if(rows==null || rows.length()==0)return;
  LinkedHashSet<String> changed=new LinkedHashSet<>();
  for(int i=0;i<rows.length();i++){JSONObject d=rows.optJSONObject(i);if(d!=null && d.optBoolean("ok")){String pkg=d.optString("pkg");if(!pkg.isEmpty())changed.add(pkg);}}
  if(changed.isEmpty())return;
  if(!Engine.beginScan()){ui.post(()->toast("增量状态刷新延后：已有其他任务"));return;}
  scanning=true;phase="正在更新本次任务的 ART 状态";scanDone=0;scanTotal=changed.size();ui.post(this::updateTaskState);
  try{for(String pkg:changed){if(Engine.scanCancelled())break;artStates.put(pkg,ArtState.query(pkg));scanDone++;if(scanDone%4==0)ui.post(this::updateTaskState);}}
  finally{scanning=false;Engine.endScan();phase="";ui.post(()->{showApps();showGroups();updateStats();updateTaskState();});}
 }
 private void confirmReset(List<String> target){if(blocked())return;if(target.isEmpty()){toast("请先选择应用");return;}styledDialog().setTitle("确认重置编译").setMessage("将对选中的 "+target.size()+" 个应用执行 pm compile --reset。不会清除应用数据，但将改变 ART 优化状态。") .setNegativeButton("取消",null).setPositiveButton("重置",(d,w)->{if(blocked())return;if(!Engine.reserveTask()){taskBlocked();return;}toast("正在启动重置任务");launchCompileService(target,true,false);}).show();}
 private boolean isSystemApp(PackageInfo p){return p.applicationInfo!=null&&(p.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;}
 private List<PackageInfo> filtered(){List<PackageInfo> list=new ArrayList<>();PackageManager pm=getPackageManager();for(PackageInfo p:packages){if(p.applicationInfo==null||isSystemApp(p)!=systemTab)continue;String name=String.valueOf(pm.getApplicationLabel(p.applicationInfo));list.add(p);}return list;}
 private RecyclerView createList(LinearLayout host,int height){RecyclerView v=new RecyclerView(this);v.setLayoutManager(new LinearLayoutManager(this));v.setNestedScrollingEnabled(true);v.setHasFixedSize(true);v.setVerticalScrollBarEnabled(true);v.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);v.setOnTouchListener((view,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)view.getParent().requestDisallowInterceptTouchEvent(true);if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)view.getParent().requestDisallowInterceptTouchEvent(false);return false;});host.addView(v,new LinearLayout.LayoutParams(-1,dp(height)));return v;}
 private interface RowFactory{View make(int index);}
 private static final class RowHolder extends RecyclerView.ViewHolder{final android.widget.FrameLayout container;RowHolder(android.widget.FrameLayout c){super(c);container=c;}}
 private RecyclerView.Adapter<RowHolder> adapter(int count,RowFactory factory){return new RecyclerView.Adapter<RowHolder>(){
  @Override public RowHolder onCreateViewHolder(android.view.ViewGroup parent,int type){android.widget.FrameLayout host=new android.widget.FrameLayout(MainActivity.this);host.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RowHolder(host);}
  @Override public void onBindViewHolder(RowHolder holder,int position){holder.container.removeAllViews();View v=factory.make(position);holder.container.addView(v,new android.widget.FrameLayout.LayoutParams(-1,-2));}
  @Override public int getItemCount(){return count;}
 };}
 private void saveListScroll(){if(appsList==null)return;LinearLayoutManager lm=(LinearLayoutManager)appsList.getLayoutManager();if(systemTab)systemScroll=lm.findFirstVisibleItemPosition();else userScroll=lm.findFirstVisibleItemPosition();}
 private void refreshSelectCounts(){if(classSelect==null)return;List<PackageInfo> list=filtered();int n=0;for(PackageInfo p:list)if(selected.contains(p.packageName))n++;classCounter.setText((systemTab?"系统应用":"用户应用")+"  "+n+" / "+list.size()+"   ·   全部已选 "+selected.size());classSelect.setOnCheckedChangeListener(null);classSelect.setChecked(!list.isEmpty()&&n==list.size());classSelect.setAlpha(n>0&&n<list.size()?.65f:1f);}
 private void showApps(){if(appsList==null)return;if(userAppTab!=null){userAppTab.setTextColor(systemTab?ACCENT:Color.WHITE);userAppTab.setBackground(clickShape(systemTab?0xffEEF2FF:ACCENT,0,10));systemAppTab.setTextColor(systemTab?Color.WHITE:ACCENT);systemAppTab.setBackground(clickShape(systemTab?ACCENT:0xffEEF2FF,0,10));}List<PackageInfo> list=filtered();if(renderedSystemTab!=null&&renderedSystemTab.booleanValue()==systemTab)saveListScroll();refreshSelectCounts();appsList.setAdapter(adapter(list.size(),position->appRow(list.get(position))));appsList.scrollToPosition(Math.max(0,systemTab?systemScroll:userScroll));renderedSystemTab=systemTab;}
 private View appRow(PackageInfo p){PackageManager pm=getPackageManager();ArtState.Entry st=artStates.get(p.packageName);String actual=st==null?"待扫描":st.filter;String desired=Store.chosenMode(this,p.packageName);boolean match=st!=null&&ArtState.matches(st,desired);LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(5),dp(5),dp(5),dp(5));item.setBackground(clickShape(selected.contains(p.packageName)?0xffEEF2FF:Color.WHITE,BORDER,10));LinearLayout outer=vertical();LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.setMargins(0,dp(2),0,dp(3));outer.addView(item,il);CheckBox cb=new CheckBox(this);cb.setButtonTintList(ColorStateList.valueOf(ACCENT));cb.setChecked(selected.contains(p.packageName));item.addView(cb);ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(p.applicationInfo));}catch(Exception ignored){icon.setImageResource(android.R.drawable.sym_def_app_icon);}LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(34),dp(34));ip.setMargins(0,0,dp(9),0);item.addView(icon,ip);LinearLayout content=vertical();item.addView(content,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(String.valueOf(pm.getApplicationLabel(p.applicationInfo)),13);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,0,0,0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(name);TextView pkg=smallLabel(p.packageName);pkg.setTextSize(10);pkg.setPadding(0,0,0,0);pkg.setSingleLine(true);pkg.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);content.addView(pkg);TextView modesLabel=smallLabel(actual+" → "+desired);modesLabel.setTextSize(10);modesLabel.setPadding(0,0,0,0);modesLabel.setTextColor(match?0xff188269:(st==null||!st.known?MUTED:0xffB96C2B));content.addView(modesLabel);cb.setOnCheckedChangeListener((v,on)->{if(on)selected.add(p.packageName);else selected.remove(p.packageName);item.setBackground(clickShape(on?0xffEEF2FF:Color.WHITE,BORDER,10));refreshSelectCounts();});item.setOnClickListener(v->cb.setChecked(!cb.isChecked()));item.setOnLongClickListener(v->{styledDialog().setTitle(p.packageName).setMessage(st==null?"ART 状态尚未扫描":st.detail).setPositiveButton("关闭",null).show();return true;});return outer;}

 private void showMetricDetails(int category){
  final String[] names={"全部应用","已完成应用","待处理应用","状态未知应用"};
  List<PackageInfo> rows=new ArrayList<>();
  for(PackageInfo pi:packages){
   ArtState.Entry state=artStates.get(pi.packageName);
   boolean unknown=state==null||!state.known;
   boolean matched=!unknown&&ArtState.matches(state,Store.chosenMode(this,pi.packageName));
   if(category==0||(category==1&&matched)||(category==2&&!unknown&&!matched)||(category==3&&unknown))rows.add(pi);
  }
  LinearLayout content=vertical();content.setPadding(dp(12),dp(6),dp(12),dp(8));
  TextView amount=smallLabel("共 "+rows.size()+" 个应用 · 仅查看当前缓存状态");content.addView(amount);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout entries=vertical();scroll.addView(entries);
  PackageManager pm=getPackageManager();
  for(PackageInfo pi:rows){
   LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(5),dp(7),dp(5),dp(7));
   ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(pi.applicationInfo));}catch(Exception ex){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
   LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(30),dp(30));iconParams.setMargins(0,0,dp(9),0);line.addView(icon,iconParams);
   LinearLayout words=vertical();line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   TextView title=text(String.valueOf(pm.getApplicationLabel(pi.applicationInfo)),13);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);words.addView(title);
   TextView pkg=smallLabel(pi.packageName);pkg.setTextSize(10);pkg.setPadding(0,0,0,0);words.addView(pkg);
   ArtState.Entry st=artStates.get(pi.packageName);String actual=st==null||!st.known?"未知":st.filter;
   TextView filter=smallLabel("当前："+actual+"  ·  目标："+Store.chosenMode(this,pi.packageName));filter.setPadding(0,0,0,0);words.addView(filter);
   entries.addView(line,new LinearLayout.LayoutParams(-1,-2));divider(entries);
  }
  if(rows.isEmpty())entries.addView(smallLabel("当前分类下没有应用"));
  content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(430)));
  styledDialog().setTitle(names[category]+"（"+rows.size()+"）").setView(content).setPositiveButton("关闭",null).show();
 }
 private void updateStats(){if(stats==null)return;JSONObject marks=Store.marks(this);int historical=0,matched=0,unknown=0;for(PackageInfo p:packages){String wanted=Store.chosenMode(this,p.packageName);if((Store.fingerprint(p)+"|"+wanted).equals(marks.optString(p.packageName)))historical++;ArtState.Entry st=artStates.get(p.packageName);if(st==null||!st.known)unknown++;else if(ArtState.matches(st,wanted))matched++;}renderMetrics(packages.size(),matched,packages.size()-matched-unknown,unknown);stats.setText("历史成功记录："+historical+" 个  ·  实时状态以 ART 查询为准");}
 private void updateTaskState(){if(taskStatus==null)return;boolean scanningNow=Engine.scanning();TaskRuntime.Snapshot live=TaskRuntime.snapshot();boolean compiling=live.running;boolean pending=Engine.busy()&&!scanningNow&&!compiling;boolean busy=scanningNow||compiling||pending;String status=scanningNow?(phase.isEmpty()?"正在扫描 ART 状态":phase)+" · "+scanDone+"/"+scanTotal:compiling?live.title+" · "+live.done+"/"+live.total:pending?"正在准备任务":"当前空闲 · 可执行操作";taskStatus.setText(status);taskStatus.setTextColor(busy?0xffBA721E:0xff188269);if(globalTaskLabel!=null){globalTaskLabel.setText((busy?"● ":"✓ ")+status);globalTaskLabel.setTextColor(busy?0xffA96721:0xff16866F);}if(globalProgress!=null){globalProgress.setVisibility(busy?View.VISIBLE:View.GONE);globalProgress.setIndeterminate(compiling&&(!live.running||live.total==0));if(compiling&&live.running&&live.total>0)globalProgress.setProgress(live.done*100/live.total);if(scanningNow){globalProgress.setIndeterminate(false);globalProgress.setProgress(scanTotal<=0?0:scanDone*100/scanTotal);}}if(stopTaskButton!=null){stopTaskButton.setEnabled(scanningNow||compiling);stopTaskButton.setAlpha(scanningNow||compiling?1f:.45f);}}
 private List<String> groupMismatch(JSONObject group){List<String> mismatch=new ArrayList<>();JSONArray pk=group.optJSONArray("packages");if(pk==null)return mismatch;String mode=group.optString("mode");for(int i=0;i<pk.length();i++){if(Engine.scanCancelled())break;String pkg=pk.optString(i);ArtState.Entry st=artStates.get(pkg);if(st!=null&&st.known&&!ArtState.matches(st,mode))mismatch.add(pkg);}return mismatch;}
 private void refreshGroup(JSONObject group){JSONArray pk=group.optJSONArray("packages");if(pk==null)return;if(blocked())return;styledDialog().setTitle("确认刷新分组 ART 状态").setMessage("即将查询本组 "+pk.length()+" 个应用的实时 ART 状态，期间无法启动编译或其他扫描。") .setNegativeButton("取消",null).setPositiveButton("刷新",(d,w)->runGroupScan(pk)).show();}
 private void runGroupScan(JSONArray pk){if(!Engine.beginScan()){taskBlocked();return;}scanDone=0;scanTotal=pk.length();scanning=true;phase="正在刷新分组 ART 状态";updateTaskState();pool.execute(()->{try{for(int i=0;i<pk.length();i++){if(Engine.scanCancelled())break;String pkg=pk.optString(i);artStates.put(pkg,ArtState.query(pkg));scanDone++;}}finally{scanning=false;Engine.endScan();phase="";ui.post(()->{showGroups();updateStats();updateTaskState();toast("分组状态刷新完成");});}}); }
 private void confirmArtScan(){if(blocked())return;styledDialog().setTitle("确认刷新全局 ART").setMessage("即将查询全部 "+packages.size()+" 个应用的 ART 状态。刷新过程中仍可浏览和选择应用。") .setNegativeButton("取消",null).setPositiveButton("开始刷新",(d,w)->scanArt()).show();}
 private void confirmCompile(boolean incremental,List<String> target){if(blocked())return;int n=target==null?packages.size():target.size();styledDialog().setTitle(incremental?"确认增量编译":"确认编译").setMessage("目标："+n+" 个应用。执行前会按当前策略检查，开始后使用已确定的包名快照。") .setNegativeButton("取消",null).setPositiveButton("确认执行",(d,w)->start(incremental,target)).show();}
 private void compileGroup(JSONObject group){if(blocked())return;JSONArray pk=group.optJSONArray("packages");if(pk==null||pk.length()==0){toast("分组中没有应用");return;}List<String> mismatch=groupMismatch(group);if(mismatch.isEmpty()){toast("没有已确认的不匹配应用，请先刷新分组状态");return;}styledDialog().setTitle("增量编译确认").setMessage("编译 "+mismatch.size()+" 个已确认不匹配的应用；未知状态不参与。").setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->start(true,mismatch)).show();}
 private void showGroups(){if(groupsList==null)return;JSONArray all=Store.groups(this);groupsList.setAdapter(adapter(all.length(),i->groupRow(all.optJSONObject(i),i)));}
 private View groupRow(JSONObject group,int ix){if(group==null)return smallLabel("无效分组");JSONArray pk=group.optJSONArray("packages");int count=pk==null?0:pk.length();int matched=0,unknown=0;for(int j=0;j<count;j++){String pkg=pk.optString(j);ArtState.Entry st=artStates.get(pkg);if(st==null||!st.known)unknown++;else if(ArtState.matches(st,group.optString("mode")))matched++;}int mismatch=count-matched-unknown;LinearLayout tile=vertical();tile.setPadding(dp(10),dp(7),dp(10),dp(8));tile.setBackground(shape(0xffF9FAFF,BORDER,12));LinearLayout wrapper=vertical();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.setMargins(0,dp(2),0,dp(5));wrapper.addView(tile,tp);TextView title=text(group.optString("name"),14);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);tile.addView(title);TextView details=smallLabel(group.optString("mode")+" · "+count+" 个应用 · 不匹配 "+mismatch+" · 未知 "+unknown);details.setPadding(0,0,0,0);tile.addView(details);LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams ar=new LinearLayout.LayoutParams(-1,dp(35));ar.setMargins(0,dp(5),0,0);tile.addView(actions,ar);String[] labels={"刷新","编译","编辑","删除"};Runnable[] callbacks={()->refreshGroup(group),()->compileGroup(group),()->editGroup(ix),()->styledDialog().setTitle("删除分组").setMessage("删除后该分组应用将恢复全局编译策略。") .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Store.deleteGroup(this,ix);reload();toast("分组已删除");}).show()};for(int j=0;j<4;j++){Button b=makeButton(labels[j],callbacks[j],j==1);b.setTextSize(11);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(35),1);if(j<3)bp.setMargins(0,0,dp(4),0);actions.addView(b,bp);}return wrapper;}
 private void editGroup(int ix){JSONObject old=ix<0?null:Store.groups(this).optJSONObject(ix);Set<String> picked=new HashSet<>();if(old!=null){JSONArray a=old.optJSONArray("packages");if(a!=null)for(int i=0;i<a.length();i++)picked.add(a.optString(i));}
  Set<String> unavailable=new HashSet<>();JSONArray gs=Store.groups(this);for(int i=0;i<gs.length();i++){if(i==ix)continue;JSONObject g=gs.optJSONObject(i);if(g==null)continue;JSONArray a=g.optJSONArray("packages");if(a!=null)for(int j=0;j<a.length();j++)unavailable.add(a.optString(j));}
  List<PackageInfo> all=Store.installedAll(this);LinearLayout body=vertical();body.setPadding(dp(18),dp(4),dp(18),dp(4));EditText name=new EditText(this);name.setSingleLine(true);name.setHint("分组名称");if(old!=null)name.setText(old.optString("name"));body.addView(name);Spinner choice=modeSpinner(old==null?"speed":old.optString("mode"));body.addView(choice);
  RadioGroup tabs=new RadioGroup(this);tabs.setOrientation(0);RadioButton user=new RadioButton(this);user.setText("用户 APP");user.setId(View.generateViewId());tabs.addView(user);RadioButton system=new RadioButton(this);system.setText("系统 APP");system.setId(View.generateViewId());tabs.addView(system);user.setChecked(true);body.addView(tabs);
  EditText searchGroup=new EditText(this);searchGroup.setSingleLine(true);searchGroup.setHint("搜索名称或包名");body.addView(searchGroup);TextView count=text("已选 "+picked.size()+" 个",13);body.addView(count);
  Button selectVisible=new Button(this);selectVisible.setText("全选当前筛选结果");body.addView(selectVisible);
  ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(330)));
  Runnable render=()->{rows.removeAllViews();String q=searchGroup.getText().toString().toLowerCase(Locale.ROOT);boolean showSystem=system.isChecked();List<PackageInfo> visible=new ArrayList<>();for(PackageInfo p:all){if(p.applicationInfo==null)continue;boolean isSystem=(p.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;if(isSystem!=showSystem)continue;String label=String.valueOf(getPackageManager().getApplicationLabel(p.applicationInfo));if(!label.toLowerCase(Locale.ROOT).contains(q)&&!p.packageName.toLowerCase(Locale.ROOT).contains(q))continue;visible.add(p);CheckBox box=new CheckBox(this);box.setText(label+"\n"+p.packageName+(unavailable.contains(p.packageName)?"（已被其他分组占用）":""));box.setTextSize(12);box.setEnabled(!unavailable.contains(p.packageName));box.setChecked(picked.contains(p.packageName));box.setOnCheckedChangeListener((v,checked)->{if(checked)picked.add(p.packageName);else picked.remove(p.packageName);count.setText("已选 "+picked.size()+" 个");});rows.addView(box);}
   selectVisible.setOnClickListener(v->{boolean allChecked=true;for(PackageInfo p:visible)if(!unavailable.contains(p.packageName)&&!picked.contains(p.packageName))allChecked=false;for(PackageInfo p:visible)if(!unavailable.contains(p.packageName)){if(allChecked)picked.remove(p.packageName);else picked.add(p.packageName);}count.setText("已选 "+picked.size()+" 个");for(int j=0;j<rows.getChildCount();j++){View item=rows.getChildAt(j);if(item instanceof CheckBox){CheckBox box=(CheckBox)item;PackageInfo info=visible.get(j);box.setChecked(picked.contains(info.packageName));}}});};
  render.run();tabs.setOnCheckedChangeListener((g,id)->render.run());searchGroup.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});
  AlertDialog dlg=styledDialog().setTitle(ix<0?"新增分组":"编辑分组").setView(body).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dlg.setOnShowListener(d->dlg.getButton(-1).setOnClickListener(v->{try{String mode=Store.FILTERS[choice.getSelectedItemPosition()];Store.saveGroup(this,ix,name.getText().toString(),mode,picked);if("everything".equals(mode))toast("已保存实验性 everything 策略，执行可能失败");dlg.dismiss();reload();}catch(Exception e){toast(e.getMessage());}}));dlg.show();
 }

 private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
 private void showHistory(){if(historyList==null)return;JSONArray h=Store.history(this);SimpleDateFormat fmt=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());historyList.setAdapter(adapter(h.length(),i->{JSONObject obj=h.optJSONObject(i);if(obj==null)return smallLabel("无效记录");LinearLayout row=vertical();row.setPadding(dp(9),dp(6),dp(9),dp(6));row.setBackground(clickShape(Color.WHITE,BORDER,11));TextView title=text(fmt.format(new Date(obj.optLong("time")))+" · "+obj.optString("type"),13);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);row.addView(title);TextView info=smallLabel("成功 "+obj.optInt("success")+" · 失败 "+obj.optInt("failed")+" · 跳过 "+obj.optInt("skipped")+" · "+(obj.optLong("duration")/1000)+"秒");info.setPadding(0,0,0,0);row.addView(info);LinearLayout box=vertical();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(2),0,dp(3));box.addView(row,lp);row.setOnClickListener(v->showTaskDetail(obj));return box;}));}
 private void showTaskDetail(JSONObject record){JSONArray entries=record.optJSONArray("details");if(entries==null||entries.length()==0){styledDialog().setTitle("任务详情").setMessage("没有可选择的执行项").setPositiveButton("关闭",null).show();return;}Set<String> picked=new HashSet<>();LinearLayout body=vertical();body.setPadding(dp(18),0,dp(18),0);TextView counter=text("已选 0 / "+entries.length(),14);body.addView(counter);CheckBox selectAll=new CheckBox(this);selectAll.setText("全选任务中的包名");body.addView(selectAll);ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(350)));
  List<CheckBox> checks=new ArrayList<>();for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;String pkg=e.optString("pkg");CheckBox cb=new CheckBox(this);cb.setText((e.optBoolean("ok")?"✓ ":"✗ ")+pkg+"（"+e.optString("mode")+"）\n"+e.optString("output"));cb.setTextSize(12);rows.addView(cb);checks.add(cb);cb.setOnCheckedChangeListener((v,selected)->{if(selected)picked.add(pkg);else picked.remove(pkg);counter.setText("已选 "+picked.size()+" / "+checks.size());});}
  selectAll.setOnCheckedChangeListener((v,all)->{for(CheckBox cb:checks)cb.setChecked(all);});AlertDialog dlg=styledDialog().setTitle("任务详情").setView(body).setNegativeButton("关闭",null).setPositiveButton("重置选中应用",null).create();dlg.setOnShowListener(d->dlg.getButton(-1).setOnClickListener(v->{if(picked.isEmpty()){toast("请先选择包名");return;}dlg.dismiss();confirmReset(new ArrayList<>(picked));}));dlg.show();}
 private List<String> installedTargetSnapshot(){List<String> snapshot=new ArrayList<>();for(PackageInfo p:Store.installed(this))snapshot.add(p.packageName);return snapshot;}
 private void start(boolean incremental,List<String> targets){if(blocked())return;final List<String> frozenTargets=targets==null?installedTargetSnapshot():new ArrayList<>(new LinkedHashSet<>(targets));if(!Engine.reserveTask()){taskBlocked();return;}toast("编译任务正在启动");launchCompileService(frozenTargets,false,incremental);}
 private void launchCompileService(List<String> targets,boolean reset,boolean incremental){
  Intent intent=new Intent(this,CompileService.class).setAction(CompileService.ACTION_RUN);
  intent.putStringArrayListExtra(CompileService.EXTRA_PACKAGES,new ArrayList<>(targets));
  intent.putExtra(CompileService.EXTRA_RESET,reset);
  intent.putExtra(CompileService.EXTRA_INCREMENTAL,incremental);
  try{startForegroundService(intent);updateTaskState();}
  catch(Exception e){Engine.releaseReserved();TaskRuntime.abort("前台服务启动失败");toast("前台服务启动失败："+e.getMessage());updateTaskState();}
 }
 private void refreshLogs(){if(logsList==null)return;long revision=TaskRuntime.revision();if(revision==logRevision)return;List<String> rows=TaskRuntime.logs();int oldSize=visibleLogs.size();if(logRevision>=0&&revision-logRevision==1&&rows.size()>=oldSize&&oldSize>0){visibleLogs=rows;logsList.getAdapter().notifyItemInserted(0);if(rows.size()==oldSize)logsList.getAdapter().notifyItemRemoved(rows.size());}else{visibleLogs=rows;logsList.getAdapter().notifyDataSetChanged();}logRevision=revision;if(followLogs&&!rows.isEmpty())logsList.scrollToPosition(0);}
 private void append(String s){TaskRuntime.line(s);ui.post(this::refreshLogs);}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onDestroy(){taskHandler.removeCallbacks(taskPulse);pool.shutdown();super.onDestroy();}
}
