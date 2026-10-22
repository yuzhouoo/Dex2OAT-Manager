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
 private static final int BG=0xffF3F6FB, INK=0xff17263E, MUTED=0xff687991, ACCENT=0xff365BD6, BORDER=0xffDFE7F2;
 private static final int SURFACE=0xffFFFFFF, SOFT=0xffEEF3FF, SUCCESS=0xff158369, WARNING=0xffAD6B14, ERROR=0xffC44250;
 private TextView stats,root, globalTaskLabel,backendState;
 private RecyclerView logsList; private long logRevision=-1; private List<String> visibleLogs=new ArrayList<>(); private boolean followLogs=true;
 private ProgressBar globalProgress;
 private LinearLayout statsGrid, appListRows, artModeSummary;
 private TextView modeFilterClear;
 private volatile int scanDone=0,scanTotal=0;
 private volatile String phase="";
 private Spinner global,period;
 private Switch automatic,backendToggle;
 private volatile boolean backendStatusPending=false;private long lastBackendPoll=0L;
 private CheckBox autoUserApps,autoSystemApps;
 private TextView taskStatus;
 private Button stopTaskButton,userAppTab,systemAppTab;
 private Spinner appStatusFilter;
 private EditText appPackageSearch;
 private int appStateSelection=0;
 private String appSearchText="";
 private String appModeFilter=null;
 private boolean systemTab=false;
 private final Handler taskHandler=new Handler(Looper.getMainLooper());
 private boolean watchedServiceTask=false;
 private long lastHistoryRevision=-1;
 private boolean dbRefreshPending=false;
 private long dbRequestedRevision=-1;
 private long lastDatabaseRevision=-1;
 private final Runnable taskPulse=new Runnable(){public void run(){
  Engine.recoverPending();refreshLogs();
  if(BackendClient.enabled(MainActivity.this)&&!backendStatusPending&&android.os.SystemClock.elapsedRealtime()-lastBackendPoll>5000){
    backendStatusPending=true;lastBackendPoll=android.os.SystemClock.elapsedRealtime();
    pool.execute(()->{
      String status=BackendClient.status();
      ui.post(()->{
        backendStatusPending=false;
        if(backendState!=null)backendState.setText("KernelSU 后台："+status);
        if(backendToggle!=null&&BackendClient.enabled(MainActivity.this))backendToggle.setChecked(true);
      });
    });
  }
  if(Store.historyRevision()!=lastHistoryRevision){lastHistoryRevision=Store.historyRevision();showHistory();}
  if(ArtDatabase.revision()!=lastDatabaseRevision && !dbRefreshPending && !Engine.scanning()){
   dbRefreshPending=true;dbRequestedRevision=ArtDatabase.revision();
   final long expected=dbRequestedRevision;
   pool.execute(()->{Map<String,ArtState.Entry> fresh=ArtDatabase.get(getApplicationContext()).snapshot(getApplicationContext());
    ui.post(()->{dbRefreshPending=false;lastDatabaseRevision=expected;artStates.clear();artStates.putAll(fresh);
     updateStats();showApps();showGroups();updateTaskState();});
   });
  }
  if(!Engine.compiling()&&!Engine.pending()&&!Engine.scanning()&&TaskRuntime.snapshot().running)TaskRuntime.abort("任务已停止，已同步状态");updateTaskState();taskHandler.postDelayed(this,1200);
 }};
 
 private final Set<String> selected=new HashSet<>();
 private List<PackageInfo> packages=new ArrayList<>();
 private final String[] modes={"speed-profile（推荐）","speed（完整 AOT）","verify（校验）","everything（实验性，可能不支持）"};
 private final int[] hours={1,3,6,12,24,48,72,168};
 private boolean loading=false;
 private final Map<String,ArtState.Entry> artStates=new ConcurrentHashMap<>();
 private volatile boolean scanning=false;
 @Override public void onCreate(Bundle b){super.onCreate(b);build();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},201);ArtDatabase.get(this).importLegacy(this);artStates.putAll(ArtDatabase.get(this).snapshot(this));lastDatabaseRevision=ArtDatabase.revision();reload();IncrementalWorker.schedule(this);refreshRoot();if(!Store.hasArtSnapshot(this)||Store.needsBootScan(this)||!Store.pref(this).getBoolean("primary_art_scan_v2",false))scanArt();}
 private AlertDialog.Builder styledDialog(){return new AlertDialog.Builder(this,android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);}
 private GradientDrawable shape(int color,int stroke,int rad){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(rad));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);t.setPadding(dp(3),dp(5),dp(3),dp(5));return t;}
 private void heading(String title){TextView v=text(title,17);v.setTextColor(INK);v.setTypeface(null,Typeface.BOLD);v.setPadding(dp(2),dp(17),0,dp(11));page.addView(v);}
 private Button makeButton(String label,Runnable action,boolean prominent){Button b=new Button(this);b.setText(label);b.setTransformationMethod(null);b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);b.setTextColor(prominent?Color.WHITE:INK);b.setBackground(clickShape(prominent?ACCENT:0xffF5F7FC, prominent?0:BORDER,12));b.setMinHeight(0);b.setMinimumHeight(0);b.setAllCaps(false);b.setPadding(dp(6),0,dp(6),0);b.setElevation(0);b.setOnClickListener(v->{b.animate().scaleX(.96f).scaleY(.96f).setDuration(65).withEndAction(()->b.animate().scaleX(1f).scaleY(1f).setDuration(110).start()).start();action.run();});return b;}
 private android.graphics.drawable.Drawable clickShape(int bg,int stroke,int rad){android.graphics.drawable.RippleDrawable ripple=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22365BD6),shape(bg,stroke,rad),null);return ripple;}
 private Button button(String label,LinearLayout p,Runnable action){Button b=makeButton(label,action,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(4),0,dp(4));p.addView(b,lp);return b;}
 private void doubleButtons(LinearLayout parent,String a,Runnable actionA,String b,Runnable actionB){LinearLayout row=new LinearLayout(this);row.setOrientation(0);parent.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));Button left=makeButton(a,actionA,false);Button right=makeButton(b,actionB,true);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(44),1);l.setMargins(0,0,dp(5),0);row.addView(left,l);LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(44),1);r.setMargins(dp(5),0,0,0);row.addView(right,r);}
 private TextView metric(String value,String label,int color){LinearLayout wrapper=vertical();wrapper.setGravity(Gravity.CENTER);wrapper.setPadding(dp(2),dp(10),dp(2),dp(10));TextView v=text(value,20);v.setTypeface(null,Typeface.BOLD);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(0,0,0,0);wrapper.addView(v);TextView t=text(label,11);t.setTextColor(MUTED);t.setGravity(Gravity.CENTER);wrapper.addView(t);return v;}
 private void renderMetrics(int total,int done,int pending,int unknown){if(statsGrid==null)return;statsGrid.removeAllViews();String[] values={""+total,""+done,""+pending,""+unknown};String[] names={"总数","已完成","待处理","未知"};int[] cs={ACCENT,SUCCESS,WARNING,MUTED};for(int i=0;i<4;i++){LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(17),dp(2),dp(17));cell.setBackground(shape(i==0?0xffEEF3FF:i==1?0xffEDF9F5:i==2?0xffFFF6E9:0xffF3F5F9,0,16));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,-2,1);if(i<3)cl.setMargins(0,0,dp(5),0);statsGrid.addView(cell,cl);TextView val=text(values[i],24);val.setTextColor(cs[i]);val.setTypeface(null,Typeface.BOLD);val.setGravity(Gravity.CENTER);cell.addView(val);TextView lab=smallLabel(names[i]);lab.setGravity(Gravity.CENTER);cell.addView(lab);final int category=i;cell.setBackground(clickShape(i==0?0xffEEF3FF:i==1?0xffEDF9F5:i==2?0xffFFF6E9:0xffF3F5F9,0,16));cell.setClickable(true);cell.setOnClickListener(v->showMetricDetails(category));}}
 private void taskBlocked(){toast("正在"+(Engine.scanning()?"获取 ART 状态":"执行编译任务")+"，请完成后再操作");}
 private boolean blocked(){if(Engine.busy()){taskBlocked();return true;}return false;}
 private TextView smallLabel(String value){TextView t=text(value,12);t.setTextColor(MUTED);return t;}
 private void divider(LinearLayout parent){View v=new View(this);v.setBackgroundColor(BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(8),0,dp(8));parent.addView(v,lp);}
 private void primary(String label,LinearLayout parent,Runnable action){Button b=makeButton(label,action,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(4),0,dp(4));parent.addView(b,lp);}
 private int index(String value){for(int i=0;i<Store.FILTERS.length;i++)if(Store.FILTERS[i].equals(value))return i;return 0;}
 private Spinner modeSpinner(String m){Spinner s=styledSpinner();s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));s.setSelection(index(m));s.setMinimumHeight(dp(48));return s;}
 private EditText styledEditText(){EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(13);e.setSingleLine(true);e.setPadding(dp(13),0,dp(12),0);e.setBackground(shape(0xffF8FAFE,BORDER,12));return e;}
 private Spinner styledSpinner(){Spinner s=new Spinner(this);s.setPadding(dp(9),0,dp(7),0);s.setBackground(shape(0xffF8FAFE,BORDER,12));s.setMinimumHeight(dp(46));return s;}
 private CheckBox styledCheckBox(){CheckBox c=new CheckBox(this);c.setTextColor(INK);c.setButtonTintList(ColorStateList.valueOf(ACCENT));c.setTextSize(13);return c;}
 private RadioButton styledRadio(){RadioButton b=new RadioButton(this);b.setTextColor(INK);b.setButtonTintList(ColorStateList.valueOf(ACCENT));b.setTextSize(13);return b;}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout card(String subtitle){LinearLayout box=vertical();box.setPadding(dp(17),dp(16),dp(17),dp(17));box.setBackground(shape(Color.WHITE,BORDER,20));box.setElevation(dp(1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));page.addView(box,lp);if(subtitle!=null){TextView title=text(subtitle,15);title.setTypeface(null,Typeface.BOLD);box.addView(title);}return box;}
 private void switchPage(int tab){activeTab=tab;for(int i=0;i<screens.length;i++){((View)screens[i].getTag()).setVisibility(i==tab?View.VISIBLE:View.GONE);navLabels[i].setTextColor(i==tab?ACCENT:MUTED);navLabels[i].setTypeface(null,i==tab?Typeface.BOLD:Typeface.NORMAL);navLabels[i].setBackground(shape(i==tab?SOFT:Color.WHITE,0,13));}}
 private void build(){
  getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout shell=vertical();shell.setBackgroundColor(BG);
  if(android.os.Build.VERSION.SDK_INT>=35){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getAttributes();getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());shell.setPadding(0,bars.top,0,bars.bottom);return insets;});}
  setContentView(shell);
  LinearLayout header=vertical();header.setPadding(dp(18),dp(14),dp(18),dp(14));header.setBackgroundColor(Color.WHITE);shell.addView(header);
  LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(titleRow);ImageView brand=new ImageView(this);brand.setImageResource(com.example.dex2oat.R.drawable.ic_dex2oat);LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(dp(40),dp(40));brandParams.setMargins(0,0,dp(11),0);titleRow.addView(brand,brandParams);TextView title=text("Dex2OAT Manager",21);title.setTypeface(null,Typeface.BOLD);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));root=text("Root 检测中",11);root.setTextColor(MUTED);titleRow.addView(root);
  LinearLayout progressShell=vertical();progressShell.setPadding(dp(19),dp(7),dp(19),dp(8));progressShell.setBackground(shape(SOFT,0,0));shell.addView(progressShell);globalTaskLabel=text("✓ 当前空闲",12);globalTaskLabel.setTextColor(0xff16866F);progressShell.addView(globalTaskLabel);globalProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);globalProgress.setMax(100);globalProgress.setVisibility(View.GONE);progressShell.addView(globalProgress,new LinearLayout.LayoutParams(-1,dp(4)));
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(12),dp(8),dp(12),dp(8));tabs.setBackgroundColor(Color.WHITE);shell.addView(tabs);
  String[] names={"概览","应用","策略","记录","日志"};String[] icons={"◈","▦","⚙","◷","≡"};
  for(int i=0;i<5;i++){final int tab=i;TextView label=text(icons[i]+" "+names[i],12);label.setGravity(Gravity.CENTER);label.setPadding(dp(2),dp(12),dp(2),dp(12));tabs.addView(label,new LinearLayout.LayoutParams(0,-2,1));navLabels[i]=label;label.setOnClickListener(v->switchPage(tab));}
  for(int i=0;i<5;i++){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=vertical();page.setPadding(dp(18),dp(17),dp(18),dp(28));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));screens[i]=page;page.setTag(scroll);}
  // Each content view is attached to its own scrolling host.
  page=screens[0];
  
  LinearLayout statCard=card(null);statCard.setPadding(dp(18),dp(16),dp(18),dp(17));statsGrid=new LinearLayout(this);statCard.addView(statsGrid,new LinearLayout.LayoutParams(-1,-2));stats=text("正在统计…",12);stats.setTextColor(MUTED);statCard.addView(stats);
  TextView hint=smallLabel("实际 ART 达标与历史成功分开统计 · 未知状态不视为失败");statCard.addView(hint);
  artModeSummary=vertical();artModeSummary.setPadding(0,dp(10),0,0);
  statCard.addView(artModeSummary,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout globalCard=card("全局默认编译模式");LinearLayout modeLine=new LinearLayout(this);modeLine.setGravity(Gravity.CENTER_VERTICAL);globalCard.addView(modeLine,new LinearLayout.LayoutParams(-1,dp(48)));global=modeSpinner(Store.global(this));modeLine.addView(global,new LinearLayout.LayoutParams(0,dp(48),1));Button saveGlobal=makeButton("保存",()->{String mode=Store.FILTERS[global.getSelectedItemPosition()];Store.global(this,mode);toast("全局模式已保存"+("everything".equals(mode)?"；设备支持情况待验证":""));updateStats();showGroups();},true);LinearLayout.LayoutParams gbutton=new LinearLayout.LayoutParams(dp(88),dp(42));gbutton.setMargins(dp(10),0,0,0);modeLine.addView(saveGlobal,gbutton);globalCard.addView(smallLabel("未加入自定义分组的应用使用全局模式；everything 为实验性选项，Android 16 可能拒绝。"));

  heading("后台执行引擎");
  LinearLayout backendCard=card(null);
  backendToggle=new Switch(this);backendToggle.setText("使用 KernelSU 后台引擎（实验性）");
  backendToggle.setTextSize(13);backendToggle.setChecked(BackendClient.enabled(this));
  backendCard.addView(backendToggle);
  backendState=smallLabel("未检测");
  backendCard.addView(backendState);
  backendToggle.setOnCheckedChangeListener((button,checked)->{
   if(Engine.busy()){button.setChecked(!checked);toast("请先完成当前任务");return;}
   pool.execute(()->{boolean available=BackendClient.available();ui.post(()->{
    if(checked&&!available){backendToggle.setChecked(false);toast("未检测到 KernelSU 后台模块，请安装模块 ZIP 并启动服务");return;}
    BackendClient.enabled(this,checked);backendState.setText(checked?"KernelSU 后台模式已启用":"APK Root 引擎模式");
   });});
  });
  pool.execute(()->{boolean available=BackendClient.available();ui.post(()->backendState.setText(available?"后台模块可连接 · "+(BackendClient.enabled(this)?"已启用":"未启用"):"未安装或未启动模块"));});
  heading("当前任务");LinearLayout running=card(null);taskStatus=text("● 空闲 · 无正在执行的任务",14);running.addView(taskStatus);stopTaskButton=makeButton("停止当前任务",()->{if(BackendClient.enabled(this)){pool.execute(()->{boolean ok=BackendClient.stop();ui.post(()->toast(ok?"已向后台发送停止请求":"后台未响应"));});}else if(Engine.busy()){Engine.stop();toast("已发送停止请求，将中止后续操作");updateTaskState();}else toast("当前没有运行中的任务");},false);running.addView(stopTaskButton,new LinearLayout.LayoutParams(-1,dp(42)));updateTaskState();
  heading("快捷操作");LinearLayout quick=card(null);doubleButtons(quick,"编译全部应用",()->{if(blocked())return;styledDialog().setTitle("全量编译确认").setMessage("强制编译全部目标应用可能造成发热及存储占用，建议充电时执行。") .setNegativeButton("取消",null).setPositiveButton("开始",(d,w)->{nextScope="编译全部应用";start(false,null);}).show();},"刷新全局 ART",()->confirmArtScan());
  page=screens[1];LinearLayout manage=card(null);
  LinearLayout operationRow=new LinearLayout(this);operationRow.setGravity(Gravity.CENTER_VERTICAL);
  manage.addView(operationRow,new LinearLayout.LayoutParams(-1,dp(46)));
  Button scanBtn=makeButton("扫描增量",()->confirmCompile(true,null),false);
  Button refreshBtn=makeButton("刷新状态",()->refreshSelectedApps(),false);
  Button compileBtn=makeButton("编译应用",()->confirmSelectedMode(),false);
  Button resetBtn=makeButton("重置应用",()->confirmReset(new ArrayList<>(selected)),false);
  Button[] operations={scanBtn,refreshBtn,compileBtn,resetBtn};
  for(int j=0;j<operations.length;j++){operations[j].setTextSize(11);operations[j].setPadding(dp(1),0,dp(1),0);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
   if(j>0)lp.leftMargin=dp(3);operationRow.addView(operations[j],lp);}
  LinearLayout appTabs=new LinearLayout(this);LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(-1,dp(48));tabParams.setMargins(0,dp(14),0,dp(8));appTabs.setPadding(dp(3),dp(3),dp(3),dp(3));appTabs.setBackground(shape(0xffEEF2FF,0,13));manage.addView(appTabs,tabParams);
  userAppTab=makeButton("用户应用",()->{saveListScroll();systemTab=false;showApps();},true);systemAppTab=makeButton("系统应用",()->{saveListScroll();systemTab=true;showApps();},false);
  LinearLayout.LayoutParams userTabLp=new LinearLayout.LayoutParams(0,-1,1);userTabLp.setMargins(0,0,dp(1),0);appTabs.addView(userAppTab,userTabLp);LinearLayout.LayoutParams systemTabLp=new LinearLayout.LayoutParams(0,-1,1);systemTabLp.setMargins(dp(1),0,0,0);appTabs.addView(systemAppTab,systemTabLp);
  LinearLayout selectLine=new LinearLayout(this);selectLine.setGravity(Gravity.CENTER_VERTICAL);manage.addView(selectLine,new LinearLayout.LayoutParams(-1,dp(43)));
  classSelect=styledCheckBox();classSelect.setButtonTintList(ColorStateList.valueOf(ACCENT));selectLine.addView(classSelect);classCounter=smallLabel("");selectLine.addView(classCounter,new LinearLayout.LayoutParams(0,-2,1));
  modeFilterClear=smallLabel("");modeFilterClear.setTextColor(ACCENT);
  modeFilterClear.setPadding(dp(5),dp(8),dp(5),dp(8));
  selectLine.addView(modeFilterClear);
  modeFilterClear.setOnClickListener(v->{appModeFilter=null;selected.clear();userScroll=0;systemScroll=0;showApps();});
  classSelect.setOnClickListener(v->{boolean on=classSelect.isChecked();for(PackageInfo a:filtered()){if(on)selected.add(a.packageName);else selected.remove(a.packageName);}showApps();});
  LinearLayout filterLine=new LinearLayout(this);filterLine.setGravity(Gravity.CENTER_VERTICAL);manage.addView(filterLine,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView filterTitle=smallLabel("状态筛选");filterLine.addView(filterTitle,new LinearLayout.LayoutParams(dp(52),-2));
  appStatusFilter=styledSpinner();
  appStatusFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"全部","已达标","未达标"}));
  appStatusFilter.setSelection(0);filterLine.addView(appStatusFilter,new LinearLayout.LayoutParams(0,dp(48),1));
  appStatusFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){if(appStateSelection!=position){appStateSelection=position;userScroll=0;systemScroll=0;showApps();}}
  });
  appPackageSearch=styledEditText();appPackageSearch.setSingleLine(true);appPackageSearch.setTextSize(13);
  appPackageSearch.setHint("搜索名称或包名");appPackageSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
  filterLine.addView(appPackageSearch,new LinearLayout.LayoutParams(0,dp(48),2));
  appPackageSearch.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){appSearchText=s.toString().trim().toLowerCase(java.util.Locale.ROOT);userScroll=0;systemScroll=0;showApps();}
   public void afterTextChanged(android.text.Editable e){}
  });
  appsList=createList(manage,220);attachFillRemaining(screens[1],appsList);
  page=screens[2];
  LinearLayout autoCard=card(null);automatic=new Switch(this);automatic.setThumbTintList(ColorStateList.valueOf(ACCENT));automatic.setText("开启自动周期扫描");automatic.setTextSize(14);automatic.setChecked(Store.auto(this));autoCard.addView(automatic);
  LinearLayout periodLine=new LinearLayout(this);periodLine.setGravity(Gravity.CENTER_VERTICAL);autoCard.addView(periodLine,new LinearLayout.LayoutParams(-1,dp(48)));period=styledSpinner();String[] intervals=new String[hours.length];for(int i=0;i<hours.length;i++)intervals[i]=hours[i]+" 小时";period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,intervals));for(int i=0;i<hours.length;i++)if(hours[i]==Store.interval(this))period.setSelection(i);periodLine.addView(period,new LinearLayout.LayoutParams(0,dp(48),1));Button savePeriod=makeButton("保存",()->{
   boolean users=autoUserApps.isChecked(),systems=autoSystemApps.isChecked();
   if(automatic.isChecked()&&!users&&!systems){toast("自动任务至少选择一种应用类型");return;}
   Store.autoTargets(this,users,systems);
   Store.auto(this,automatic.isChecked());Store.interval(this,hours[period.getSelectedItemPosition()]);
   IncrementalWorker.schedule(this);toast("自动增量编译设置已保存");
  },true);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(88),dp(42));ap.setMargins(dp(10),0,0,0);periodLine.addView(savePeriod,ap);
  LinearLayout types=new LinearLayout(this);types.setOrientation(LinearLayout.HORIZONTAL);
  autoUserApps=styledCheckBox();autoUserApps.setText("用户应用");autoUserApps.setTextSize(13);autoUserApps.setChecked(Store.autoUsers(this));
  autoSystemApps=styledCheckBox();autoSystemApps.setText("系统应用");autoSystemApps.setTextSize(13);autoSystemApps.setChecked(Store.autoSystems(this));
  types.addView(autoUserApps,new LinearLayout.LayoutParams(0,dp(46),1));
  types.addView(autoSystemApps,new LinearLayout.LayoutParams(0,dp(46),1));
  autoCard.addView(types);
  autoCard.addView(smallLabel("只检查勾选的应用类型；未变化且已达标的应用直接跳过。"));
  LinearLayout groupHeading=new LinearLayout(this);groupHeading.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);page.addView(groupHeading,new LinearLayout.LayoutParams(-1,dp(54)));Button addGroup=makeButton("＋ 新建编译分组",()->editGroup(-1),true);LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(dp(150),dp(40));groupHeading.addView(addGroup,addLp);
  TextView sub=smallLabel("分组策略优先于全局模式，每个应用只能加入一个分组。");page.addView(sub);
  LinearLayout groupHost=card(null);groupsList=createList(groupHost,220);attachFillRemaining(screens[2],groupsList);
  page=screens[3];LinearLayout historyActions=card(null);button("清空执行历史",historyActions,()->styledDialog().setMessage("清空任务记录？成功编译标记将保留。") .setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{Store.clearHistory(this);showHistory();}).show());LinearLayout historyCard=card(null);historyList=createList(historyCard,220);attachFillRemaining(screens[3],historyList);
  page=screens[4];page.setPadding(dp(14),dp(12),dp(14),dp(12));
  LinearLayout logActions=card(null);button("清除历史日志",logActions,()->styledDialog().setTitle("清除历史日志").setMessage("清空当前日志列表？不会删除任务执行记录。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{TaskRuntime.clearLogs();refreshLogs();toast("历史日志已清除");}).show());
  LinearLayout logsCard=card(null);logsCard.setPadding(dp(9),dp(10),dp(9),dp(10));logsList=createList(logsCard,220);attachFillRemaining(screens[4],logsList);logsList.setAdapter(new RecyclerView.Adapter<RowHolder>(){public RowHolder onCreateViewHolder(android.view.ViewGroup parent,int type){android.widget.FrameLayout host=new android.widget.FrameLayout(MainActivity.this);host.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RowHolder(host);}public void onBindViewHolder(RowHolder holder,int position){holder.container.removeAllViews();TextView item=smallLabel(visibleLogs.get(position));if(isFailureLog(visibleLogs.get(position)))item.setTextColor(0xffC44747);item.setTextSize(12);item.setTextIsSelectable(true);item.setPadding(dp(8),dp(6),dp(8),dp(6));holder.container.addView(item,new android.widget.FrameLayout.LayoutParams(-1,-2));}public int getItemCount(){return visibleLogs.size();}});logsList.addOnScrollListener(new RecyclerView.OnScrollListener(){
   @Override public void onScrolled(RecyclerView rv,int dx,int dy){if(dy>0)followLogs=false;else if(!rv.canScrollVertically(-1))followLogs=true;}
  });
  refreshLogs();
  switchPage(getIntent().getBooleanExtra("open_logs",false)?4:0);taskHandler.post(taskPulse);
 }
 private void reload(){packages=Store.installedAll(this);showApps();showGroups();showHistory();updateStats();}
 @Override protected void onResume(){super.onResume();Engine.recoverPending();updateTaskState();refreshLogs();lastHistoryRevision=Store.historyRevision();showHistory();if(root!=null)refreshRoot();}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_logs",false))switchPage(4);}
 private void refreshRoot(){if(Engine.busy())return;pool.execute(()->{if(Engine.busy())return;boolean ok=Engine.rootOk();ui.post(()->{root.setText(ok?"（Root 已授权）":"（Root 未授权）");root.setTextColor(ok?0xff188269:0xffC85A3C);});});}
 private void scanArt(){if(!Engine.beginScan()){taskBlocked();return;}scanning=true;scanDone=0;scanTotal=packages.size();phase="正在获取 ART 状态";updateTaskState();pool.execute(()->{try{if(!Engine.rootOk())return;for(PackageInfo p:new ArrayList<>(packages)){if(Engine.scanCancelled())break;ArtState.Entry item=ArtState.query(p.packageName);artStates.put(p.packageName,item);ArtDatabase.get(getApplicationContext()).update(getApplicationContext(),p.packageName,item,"full-scan",null);scanDone++;if(scanDone%8==0)ui.post(()->{if(activeTab==2)showGroups();updateStats();updateTaskState();});}}finally{if(!Engine.scanCancelled()&&scanDone==scanTotal){Store.markArtScanComplete(getApplicationContext());Store.pref(getApplicationContext()).edit().putBoolean("primary_art_scan_v2",true).apply();}scanning=false;Engine.endScan();phase="";ui.post(()->{showApps();showGroups();updateStats();updateTaskState();});}}); }
 private void refreshTaskPackages(JSONObject record){
  if(record==null)return;
  JSONArray rows=record.optJSONArray("details");if(rows==null || rows.length()==0)return;
  LinkedHashSet<String> changed=new LinkedHashSet<>();
  for(int i=0;i<rows.length();i++){JSONObject d=rows.optJSONObject(i);if(d!=null && d.optBoolean("ok")){String pkg=d.optString("pkg");if(!pkg.isEmpty())changed.add(pkg);}}
  if(changed.isEmpty())return;
  if(!Engine.beginScan()){ui.post(()->toast("增量状态刷新延后：已有其他任务"));return;}
  scanning=true;phase="正在更新本次任务的 ART 状态";scanDone=0;scanTotal=changed.size();ui.post(this::updateTaskState);
  try{for(String pkg:changed){if(Engine.scanCancelled())break;ArtState.Entry item=ArtState.query(pkg);artStates.put(pkg,item);ArtDatabase.get(getApplicationContext()).update(getApplicationContext(),pkg,item,"post-task-scan",null);scanDone++;if(scanDone%4==0)ui.post(this::updateTaskState);}}
  finally{scanning=false;Engine.endScan();phase="";ui.post(()->{showApps();showGroups();updateStats();updateTaskState();});}
 }
 private void confirmReset(List<String> target){if(blocked())return;if(target.isEmpty()){toast("请先选择应用");return;}styledDialog().setTitle("确认重置编译").setMessage("将对选中的 "+target.size()+" 个应用执行 pm compile --reset。不会清除应用数据，但将改变 ART 优化状态。") .setNegativeButton("取消",null).setPositiveButton("重置",(d,w)->{if(blocked())return;if(!Engine.reserveTask()){taskBlocked();return;}toast("正在启动重置任务");launchCompileService(target,true,false,"重置选中应用");}).show();}
 private boolean isSystemApp(PackageInfo p){return p.applicationInfo!=null&&(p.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;}
 private List<PackageInfo> filtered(){List<PackageInfo> list=new ArrayList<>();for(PackageInfo p:packages){
  if(p.applicationInfo==null||isSystemApp(p)!=systemTab)continue;
  ArtState.Entry state=artStates.get(p.packageName);
  if(appModeFilter!=null&&!appModeFilter.equals(state==null||!state.known?"未知":state.filter))continue;
  boolean match=state!=null&&ArtState.matches(state,Store.chosenMode(this,p.packageName));
  if(appStateSelection==1&&!match)continue;
  if(appStateSelection==2&&(state==null||!state.known||match))continue;
  if(!appSearchText.isEmpty()){
   String pkg=p.packageName.toLowerCase(java.util.Locale.ROOT);
   String label=String.valueOf(getPackageManager().getApplicationLabel(p.applicationInfo)).toLowerCase(java.util.Locale.ROOT);
   if(!pkg.contains(appSearchText)&&!label.contains(appSearchText))continue;
  }
  list.add(p);
 }return list;}
 private void attachFillRemaining(LinearLayout screen,RecyclerView list){
  // Screen content is contained in a viewport-sized ScrollView. Occupy remaining visible
  // space after the toolbars, with bottom padding kept inside the viewport's system insets.
  ScrollView scroll=(ScrollView)screen.getTag();
  scroll.getViewTreeObserver().addOnGlobalLayoutListener(()->{
   if(scroll.getHeight()<=0 || list.getParent()==null)return;
   int top=0;View child=list;
   while(child!=null&&child!=screen){
    top+=child.getTop();
    if(!(child.getParent() instanceof View))break;
    child=(View)child.getParent();
   }
   int desired=Math.max(dp(160),scroll.getHeight()-top-dp(48));
   android.view.ViewGroup.LayoutParams lp=list.getLayoutParams();
   if(lp.height!=desired){lp.height=desired;list.setLayoutParams(lp);}
  });
 }
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
 private void showApps(){if(appsList==null)return;
 if(modeFilterClear!=null){modeFilterClear.setText(appModeFilter==null?"":"ART "+appModeFilter+" ×");modeFilterClear.setVisibility(appModeFilter==null?View.GONE:View.VISIBLE);}if(userAppTab!=null){userAppTab.setTextColor(systemTab?ACCENT:Color.WHITE);userAppTab.setBackground(clickShape(systemTab?0xffEEF2FF:ACCENT,0,10));systemAppTab.setTextColor(systemTab?Color.WHITE:ACCENT);systemAppTab.setBackground(clickShape(systemTab?ACCENT:0xffEEF2FF,0,10));}List<PackageInfo> list=filtered();if(renderedSystemTab!=null&&renderedSystemTab.booleanValue()==systemTab)saveListScroll();refreshSelectCounts();appsList.setAdapter(adapter(list.size(),position->appRow(list.get(position))));appsList.scrollToPosition(Math.max(0,systemTab?systemScroll:userScroll));renderedSystemTab=systemTab;}
 private View appRow(PackageInfo p){PackageManager pm=getPackageManager();ArtState.Entry st=artStates.get(p.packageName);String actual=st==null?"待扫描":st.filter;String desired=Store.chosenMode(this,p.packageName);boolean match=st!=null&&ArtState.matches(st,desired);LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(5),dp(5),dp(5),dp(5));item.setBackground(clickShape(selected.contains(p.packageName)?SOFT:Color.WHITE,BORDER,14));LinearLayout outer=vertical();LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.setMargins(0,dp(2),0,dp(3));outer.addView(item,il);CheckBox cb=styledCheckBox();cb.setButtonTintList(ColorStateList.valueOf(ACCENT));cb.setChecked(selected.contains(p.packageName));item.addView(cb);ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(p.applicationInfo));}catch(Exception ignored){icon.setImageResource(android.R.drawable.sym_def_app_icon);}LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(34),dp(34));ip.setMargins(0,0,dp(9),0);item.addView(icon,ip);LinearLayout content=vertical();item.addView(content,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(String.valueOf(pm.getApplicationLabel(p.applicationInfo)),13);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,0,0,0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(name);TextView pkg=smallLabel(p.packageName);pkg.setTextSize(10);pkg.setPadding(0,0,0,0);pkg.setSingleLine(true);pkg.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);content.addView(pkg);TextView modesLabel=smallLabel(actual+" → "+desired);modesLabel.setTextSize(10);modesLabel.setPadding(0,0,0,0);modesLabel.setTextColor(match?0xff188269:(st==null||!st.known?MUTED:0xffB96C2B));content.addView(modesLabel);cb.setOnCheckedChangeListener((v,on)->{if(on)selected.add(p.packageName);else selected.remove(p.packageName);item.setBackground(clickShape(on?0xffEEF2FF:Color.WHITE,BORDER,10));refreshSelectCounts();});item.setOnClickListener(v->cb.setChecked(!cb.isChecked()));item.setOnLongClickListener(v->{styledDialog().setTitle(p.packageName).setMessage(st==null?"ART 状态尚未扫描":st.detail).setPositiveButton("关闭",null).show();return true;});return outer;}

 private void showMetricDetails(int category){
  final String[] names={"全部应用","已完成应用","待处理应用","状态未知应用"};
  final List<PackageInfo> users=new ArrayList<>(),systems=new ArrayList<>();
  for(PackageInfo pi:packages){
   ArtState.Entry state=artStates.get(pi.packageName);
   boolean unknown=state==null||!state.known;
   boolean matched=!unknown&&ArtState.matches(state,Store.chosenMode(this,pi.packageName));
   if(category!=0&&!(category==1&&matched)&&!(category==2&&!unknown&&!matched)&&!(category==3&&unknown))continue;
   (isSystemApp(pi)?systems:users).add(pi);
  }
  LinearLayout body=vertical();body.setPadding(dp(12),dp(6),dp(12),dp(8));
  LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(3),dp(3),dp(3),dp(3));tabs.setBackground(shape(0xffEEF2FF,0,13));
  LinearLayout.LayoutParams tabsLp=new LinearLayout.LayoutParams(-1,dp(48));tabsLp.setMargins(0,0,0,dp(8));body.addView(tabs,tabsLp);
  Button userButton=makeButton("用户应用 ("+users.size()+")",()->{},true);
  Button systemButton=makeButton("系统应用 ("+systems.size()+")",()->{},false);
  LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.setMargins(0,0,dp(1),0);
  LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.setMargins(dp(1),0,0,0);
  tabs.addView(userButton,left);tabs.addView(systemButton,right);
  TextView amount=smallLabel("");body.addView(amount);
  RecyclerView listing=createList(body,420);
  listing.setItemAnimator(null);
  final boolean[] showingSystem={false};
  Runnable render=()->{
   boolean sys=showingSystem[0];final List<PackageInfo> active=sys?systems:users;
   userButton.setTextColor(sys?ACCENT:Color.WHITE);userButton.setBackground(clickShape(sys?0xffEEF2FF:ACCENT,0,10));
   systemButton.setTextColor(sys?Color.WHITE:ACCENT);systemButton.setBackground(clickShape(sys?ACCENT:0xffEEF2FF,0,10));
   amount.setText("当前显示 "+active.size()+" 个应用 · 仅查看 ART 缓存状态");
   listing.setAdapter(adapter(active.size(),position->{
    PackageInfo pi=active.get(position);PackageManager pm=getPackageManager();
    LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(5),dp(7),dp(5),dp(7));
    ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(pi.applicationInfo));}
    catch(Exception ex){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
    LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(30),dp(30));iconParams.setMargins(0,0,dp(9),0);line.addView(icon,iconParams);
    LinearLayout words=vertical();line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    TextView title=text(String.valueOf(pm.getApplicationLabel(pi.applicationInfo)),13);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);words.addView(title);
    TextView pkg=smallLabel(pi.packageName);pkg.setTextSize(10);pkg.setPadding(0,0,0,0);words.addView(pkg);
    ArtState.Entry st=artStates.get(pi.packageName);String actual=st==null||!st.known?"未知":st.filter;
    TextView filter=smallLabel("当前："+actual+"  ·  目标："+Store.chosenMode(this,pi.packageName));filter.setPadding(0,0,0,0);words.addView(filter);
    return line;
   }));
   listing.scrollToPosition(0);
  };
  userButton.setOnClickListener(v->{if(showingSystem[0]){showingSystem[0]=false;render.run();}});
  systemButton.setOnClickListener(v->{if(!showingSystem[0]){showingSystem[0]=true;render.run();}});
  render.run();
  styledDialog().setTitle(names[category]+"（"+(users.size()+systems.size())+"）").setView(body).setPositiveButton("关闭",null).show();
 }
 private void updateStats(){if(stats==null)return;JSONObject marks=Store.marks(this);int historical=0,matched=0,unknown=0;
 LinkedHashMap<String,Integer> userModes=new LinkedHashMap<>(),systemModes=new LinkedHashMap<>();
 for(PackageInfo p:packages){
  String wanted=Store.chosenMode(this,p.packageName);
  if((Store.fingerprint(p)+"|"+wanted).equals(marks.optString(p.packageName)))historical++;
  ArtState.Entry st=artStates.get(p.packageName);
  if(st==null||!st.known)unknown++;else if(ArtState.matches(st,wanted))matched++;
  if(p.applicationInfo!=null){
   String mode=st==null||!st.known?"未知":st.filter;
   Map<String,Integer> histogram=isSystemApp(p)?systemModes:userModes;
   histogram.put(mode,histogram.getOrDefault(mode,0)+1);
  }
 }
 renderMetrics(packages.size(),matched,packages.size()-matched-unknown,unknown);
 stats.setText("历史成功记录："+historical+" 个  ·  实时状态以 ART 查询为准");
 renderArtModeSummary(userModes,systemModes);
}
private void renderArtModeSummary(Map<String,Integer> userModes,Map<String,Integer> systemModes){
 if(artModeSummary==null)return;
 artModeSummary.removeAllViews();
 renderArtModeSection("用户应用",false,userModes);
 renderArtModeSection("系统应用",true,systemModes);
}
private void renderArtModeSection(String title,boolean system,Map<String,Integer> histogram){
 TextView titleView=text(title,13);titleView.setTypeface(null,Typeface.BOLD);
 titleView.setPadding(0,dp(7),0,dp(4));artModeSummary.addView(titleView);
 if(histogram.isEmpty()){artModeSummary.addView(smallLabel("暂无应用"));return;}
 String[] order={"everything","speed","speed-profile","verify","quicken","extract","assume-verified","run-from-apk","run-from-apk-fallback","混合","未知"};
 List<String> modes=new ArrayList<>(histogram.keySet());
 modes.sort((a,b)->{int ai=java.util.Arrays.asList(order).indexOf(a),bi=java.util.Arrays.asList(order).indexOf(b);
  if(ai<0)ai=order.length;if(bi<0)bi=order.length;return ai==bi?a.compareTo(b):Integer.compare(ai,bi);});
 LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
 artModeSummary.addView(line,new LinearLayout.LayoutParams(-1,-2));
 int columns=0;
 for(String mode:modes){
  if(columns==2){line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);artModeSummary.addView(line,new LinearLayout.LayoutParams(-1,-2));columns=0;}
  final String chosen=mode;
  TextView chip=text(mode+"："+histogram.get(mode),12);
  chip.setTextColor(ACCENT);chip.setGravity(Gravity.CENTER_VERTICAL);
  chip.setPadding(dp(13),dp(11),dp(8),dp(11));
  chip.setBackground(clickShape(SOFT,BORDER,13));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
  lp.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(chip,lp);
  chip.setOnClickListener(v->openArtModeApplications(system,chosen));columns++;
 }
 if(columns==1){View filler=new View(this);line.addView(filler,new LinearLayout.LayoutParams(0,1,1));}
}
private void openArtModeApplications(boolean system,String mode){
 systemTab=system;appModeFilter=mode;appStateSelection=0;appSearchText="";
 if(appStatusFilter!=null)appStatusFilter.setSelection(0);
 if(appPackageSearch!=null)appPackageSearch.setText("");
 userScroll=0;systemScroll=0;renderedSystemTab=null;
 switchPage(1);showApps();
 toast((system?"系统应用":"用户应用")+" · "+mode+"（点击应用页分类可切换，模式筛选保留）");
}
 private void updateTaskState(){if(taskStatus==null)return;boolean scanningNow=Engine.scanning();TaskRuntime.Snapshot live=TaskRuntime.snapshot();boolean pending=Engine.pending()&&!scanningNow;
 boolean compiling=Engine.compiling()&&!scanningNow;
 boolean busy=scanningNow||compiling||pending;String status=scanningNow?(phase.isEmpty()?"正在扫描 ART 状态":phase)+" · "+scanDone+"/"+scanTotal:compiling?(live.running?live.title+" · "+live.done+"/"+live.total:"正在执行编译 / 重置任务"):pending?"正在准备任务":"当前空闲 · 可执行操作";taskStatus.setText(status);taskStatus.setTextColor(busy?0xffBA721E:0xff188269);if(globalTaskLabel!=null){globalTaskLabel.setText((busy?"● ":"✓ ")+status);globalTaskLabel.setTextColor(busy?0xffA96721:0xff16866F);}if(globalProgress!=null){globalProgress.setVisibility(busy?View.VISIBLE:View.GONE);globalProgress.setIndeterminate(compiling&&(!live.running||live.total==0));if(compiling&&live.running&&live.total>0)globalProgress.setProgress(live.done*100/live.total);if(scanningNow){globalProgress.setIndeterminate(false);globalProgress.setProgress(scanTotal<=0?0:scanDone*100/scanTotal);}}if(stopTaskButton!=null){stopTaskButton.setEnabled(scanningNow||compiling||pending);stopTaskButton.setAlpha(scanningNow||compiling||pending?1f:.45f);}}
 private List<String> groupMismatch(JSONObject group){List<String> mismatch=new ArrayList<>();JSONArray pk=group.optJSONArray("packages");if(pk==null)return mismatch;String mode=group.optString("mode");for(int i=0;i<pk.length();i++){if(Engine.scanCancelled())break;String pkg=pk.optString(i);ArtState.Entry st=artStates.get(pkg);if(st!=null&&st.known&&!ArtState.matches(st,mode))mismatch.add(pkg);}return mismatch;}
 private void refreshGroup(JSONObject group){JSONArray pk=group.optJSONArray("packages");if(pk==null)return;if(blocked())return;styledDialog().setTitle("确认刷新分组 ART 状态").setMessage("即将查询本组 "+pk.length()+" 个应用的实时 ART 状态，期间无法启动编译或其他扫描。") .setNegativeButton("取消",null).setPositiveButton("刷新",(d,w)->runGroupScan(pk)).show();}
 private void scanSelectedArt(List<String> targets){
  if(!Engine.beginScan()){taskBlocked();return;}
  scanning=true;scanDone=0;scanTotal=targets.size();phase="正在刷新选中应用 ART";updateTaskState();
  pool.execute(()->{try{
   for(String pkg:targets){
    if(Engine.scanCancelled())break;
    ArtState.Entry entry=ArtState.query(pkg);
    artStates.put(pkg,entry);
    ArtDatabase.get(getApplicationContext()).update(getApplicationContext(),pkg,entry,"selected-scan",null);
    scanDone++;if(scanDone%4==0)ui.post(this::updateTaskState);
   }
  }finally{
   scanning=false;Engine.endScan();phase="";
   ui.post(()->{showApps();showGroups();updateStats();updateTaskState();toast("选中应用 ART 刷新结束："+scanDone+"/"+scanTotal);});
  }});
 }
 private void runGroupScan(JSONArray pk){if(!Engine.beginScan()){taskBlocked();return;}scanDone=0;scanTotal=pk.length();scanning=true;phase="正在刷新分组 ART 状态";updateTaskState();pool.execute(()->{try{for(int i=0;i<pk.length();i++){if(Engine.scanCancelled())break;String pkg=pk.optString(i);ArtState.Entry item=ArtState.query(pkg);artStates.put(pkg,item);ArtDatabase.get(getApplicationContext()).update(getApplicationContext(),pkg,item,"group-scan",null);scanDone++;}}finally{scanning=false;Engine.endScan();phase="";ui.post(()->{showGroups();updateStats();updateTaskState();toast("分组状态刷新完成");});}}); }
 private void confirmArtScan(){if(blocked())return;styledDialog().setTitle("确认刷新全局 ART").setMessage("即将查询全部 "+packages.size()+" 个应用的 ART 状态。刷新过程中仍可浏览和选择应用。") .setNegativeButton("取消",null).setPositiveButton("开始刷新",(d,w)->scanArt()).show();}
 private void refreshSelectedApps(){
  if(blocked())return;
  if(selected.isEmpty()){toast("请先选择需要刷新的应用");return;}
  List<String> targets=new ArrayList<>(selected);Collections.sort(targets);
  styledDialog().setTitle("刷新选中应用 ART").setMessage("仅查询已选中的 "+targets.size()+" 个应用，不执行编译。")
   .setNegativeButton("取消",null).setPositiveButton("刷新",(d,w)->scanSelectedArt(targets)).show();
 }
 private void confirmSelectedMode(){
  if(blocked())return;
  if(selected.isEmpty()){toast("请先选择应用");return;}
  List<String> targets=new ArrayList<>(selected);
  LinearLayout body=vertical();body.setPadding(dp(18),dp(8),dp(18),dp(8));
  body.addView(smallLabel("本次选择 "+targets.size()+" 个应用，默认全局编译模式，仅对本次有效。"));
  Spinner oneTime=modeSpinner(Store.global(this));body.addView(oneTime,new LinearLayout.LayoutParams(-1,dp(54)));
  styledDialog().setTitle("确认本次编译模式").setView(body).setNegativeButton("取消",null)
   .setPositiveButton("开始",(d,w)->startCompileWithMode(false,targets,"编译选中应用",Store.FILTERS[oneTime.getSelectedItemPosition()])).show();
 }
 private void confirmCompile(boolean incremental,List<String> target){if(blocked())return;int n=target==null?packages.size():target.size();styledDialog().setTitle(incremental?"确认增量编译":"确认编译").setMessage("目标："+n+" 个应用。执行前会按当前策略检查，开始后使用已确定的包名快照。") .setNegativeButton("取消",null).setPositiveButton("确认执行",(d,w)->{nextScope=incremental?"扫描增量应用":"编译选中应用";start(incremental,target);}).show();}
 private void compileGroup(JSONObject group){
 if(blocked())return;JSONArray pk=group.optJSONArray("packages");
 if(pk==null||pk.length()==0){toast("分组中没有应用");return;}
 String mode=group.optString("mode",Store.global(this));
 List<String> targets=new ArrayList<>();for(int i=0;i<pk.length();i++){String pkg=pk.optString(i);if(!pkg.isEmpty())targets.add(pkg);}
 styledDialog().setTitle("编译分组："+group.optString("name"))
  .setMessage("按分组模式 "+mode+" 增量检查 "+targets.size()+" 个应用，未知和不匹配的应用都会处理；已达标跳过。")
  .setNegativeButton("取消",null).setPositiveButton("开始",(dialog,which)->startCompileWithMode(true,targets,"自定义分组："+group.optString("name"),mode)).show();
}
 private void showGroups(){if(groupsList==null)return;JSONArray all=Store.groups(this);groupsList.setAdapter(adapter(all.length(),i->groupRow(all.optJSONObject(i),i)));}
 private View groupRow(JSONObject group,int ix){if(group==null)return smallLabel("无效分组");JSONArray pk=group.optJSONArray("packages");int count=pk==null?0:pk.length();int matched=0,unknown=0;for(int j=0;j<count;j++){String pkg=pk.optString(j);ArtState.Entry st=artStates.get(pkg);if(st==null||!st.known)unknown++;else if(ArtState.matches(st,group.optString("mode")))matched++;}int mismatch=count-matched-unknown;LinearLayout tile=vertical();tile.setPadding(dp(10),dp(7),dp(10),dp(8));tile.setBackground(shape(0xffF9FAFF,BORDER,12));LinearLayout wrapper=vertical();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.setMargins(0,dp(2),0,dp(5));wrapper.addView(tile,tp);TextView title=text(group.optString("name"),14);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);tile.addView(title);TextView details=smallLabel(group.optString("mode")+" · "+count+" 个应用 · 不匹配 "+mismatch+" · 未知 "+unknown);details.setPadding(0,0,0,0);tile.addView(details);LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams ar=new LinearLayout.LayoutParams(-1,dp(35));ar.setMargins(0,dp(5),0,0);tile.addView(actions,ar);String[] labels={"刷新","编译","编辑","删除"};Runnable[] callbacks={()->refreshGroup(group),()->compileGroup(group),()->editGroup(ix),()->styledDialog().setTitle("删除分组").setMessage("删除后该分组应用将恢复全局编译策略。") .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{Store.deleteGroup(this,ix);reload();toast("分组已删除");}).show()};for(int j=0;j<4;j++){Button b=makeButton(labels[j],callbacks[j],false);b.setTextSize(11);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(35),1);if(j<3)bp.setMargins(0,0,dp(4),0);actions.addView(b,bp);}return wrapper;}
 private void renderGroupChecks(LinearLayout rows,List<PackageInfo> visible,Set<String> picked){
  for(int i=0;i<visible.size()&&i<rows.getChildCount();i++){
   View row=rows.getChildAt(i);
   if(row instanceof LinearLayout){
    LinearLayout container=(LinearLayout)row;
    if(container.getChildCount()>0&&container.getChildAt(0) instanceof CheckBox){
     ((CheckBox)container.getChildAt(0)).setChecked(picked.contains(visible.get(i).packageName));
    }
   }
  }
 }
 private void editGroup(int ix){JSONObject old=ix<0?null:Store.groups(this).optJSONObject(ix);Set<String> picked=new HashSet<>();if(old!=null){JSONArray a=old.optJSONArray("packages");if(a!=null)for(int i=0;i<a.length();i++)picked.add(a.optString(i));}
  Set<String> unavailable=new HashSet<>();JSONArray gs=Store.groups(this);for(int i=0;i<gs.length();i++){if(i==ix)continue;JSONObject g=gs.optJSONObject(i);if(g==null)continue;JSONArray a=g.optJSONArray("packages");if(a!=null)for(int j=0;j<a.length();j++)unavailable.add(a.optString(j));}
  List<PackageInfo> all=Store.installedAll(this);LinearLayout body=vertical();body.setPadding(dp(18),dp(4),dp(18),dp(4));EditText name=styledEditText();name.setSingleLine(true);name.setHint("分组名称");if(old!=null)name.setText(old.optString("name"));body.addView(name);Spinner choice=modeSpinner(old==null?"speed":old.optString("mode"));body.addView(choice);
  RadioGroup tabs=new RadioGroup(this);tabs.setOrientation(0);RadioButton user=styledRadio();user.setText("用户 APP");user.setId(View.generateViewId());tabs.addView(user);RadioButton system=styledRadio();system.setText("系统 APP");system.setId(View.generateViewId());tabs.addView(system);user.setChecked(true);body.addView(tabs);
  EditText searchGroup=styledEditText();searchGroup.setSingleLine(true);searchGroup.setHint("搜索名称或包名");body.addView(searchGroup);TextView count=text("已选 "+picked.size()+" 个",13);body.addView(count);
  Button selectVisible=new Button(this);selectVisible.setText("全选当前筛选结果");body.addView(selectVisible);
  ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(330)));
  Runnable render=()->{
   rows.removeAllViews();String q=searchGroup.getText().toString().toLowerCase(Locale.ROOT);
   boolean showSystem=system.isChecked();List<PackageInfo> visible=new ArrayList<>();
   for(PackageInfo p:all){
    if(p.applicationInfo==null)continue;
    boolean isSystem=(p.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
    if(isSystem!=showSystem)continue;
    String label=String.valueOf(getPackageManager().getApplicationLabel(p.applicationInfo));
    if(!label.toLowerCase(Locale.ROOT).contains(q)&&!p.packageName.toLowerCase(Locale.ROOT).contains(q))continue;
    visible.add(p);
    boolean occupied=unavailable.contains(p.packageName);
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(5),dp(5),dp(5),dp(5));row.setBackground(clickShape(Color.WHITE,BORDER,14));
    LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.setMargins(0,dp(2),0,dp(3));
    rows.addView(row,rowLp);
    CheckBox checkbox=styledCheckBox();checkbox.setButtonTintList(ColorStateList.valueOf(ACCENT));
    checkbox.setChecked(picked.contains(p.packageName));checkbox.setEnabled(!occupied);row.addView(checkbox);
    ImageView icon=new ImageView(this);
    try{icon.setImageDrawable(getPackageManager().getApplicationIcon(p.applicationInfo));}
    catch(Exception ignored){icon.setImageResource(android.R.drawable.sym_def_app_icon);}
    LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(34),dp(34));iconLp.setMargins(0,0,dp(9),0);row.addView(icon,iconLp);
    LinearLayout information=vertical();row.addView(information,new LinearLayout.LayoutParams(0,-2,1));
    TextView appName=text(label,13);appName.setTypeface(null,Typeface.BOLD);appName.setSingleLine(true);
    appName.setEllipsize(android.text.TextUtils.TruncateAt.END);appName.setPadding(0,0,0,0);information.addView(appName);
    TextView packageName=smallLabel(p.packageName);packageName.setTextSize(10);
    packageName.setSingleLine(true);packageName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    packageName.setPadding(0,0,0,0);information.addView(packageName);
    ArtState.Entry state=artStates.get(p.packageName);
    String current=state==null?"待扫描":state.filter;
    String target=Store.FILTERS[choice.getSelectedItemPosition()];
    TextView modes=smallLabel("ART "+current+" → "+target+(occupied?" · 已被其他分组占用":""));
    modes.setTextSize(10);modes.setPadding(0,0,0,0);information.addView(modes);
    boolean match=state!=null&&ArtState.matches(state,target);
    TextView outcome=text(occupied?"不可选择":(state==null||!state.known)?"未知":match?"已达标":"未达标",10);
    outcome.setTextColor(occupied?MUTED:match?0xff188269:0xffB16F28);
    row.addView(outcome);
    checkbox.setOnCheckedChangeListener((button,checked)->{
     if(checked)picked.add(p.packageName);else picked.remove(p.packageName);
     count.setText("已选 "+picked.size()+" 个");
    });
    row.setOnClickListener(v->{if(!occupied)checkbox.setChecked(!checkbox.isChecked());});
   }
   selectVisible.setOnClickListener(v->{
    boolean allChecked=true;
    for(PackageInfo p:visible)if(!unavailable.contains(p.packageName)&&!picked.contains(p.packageName))allChecked=false;
    for(PackageInfo p:visible)if(!unavailable.contains(p.packageName)){
     if(allChecked)picked.remove(p.packageName);else picked.add(p.packageName);
    }
    count.setText("已选 "+picked.size()+" 个");renderGroupChecks(rows,visible,picked);
   });
  };
  render.run();choice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){render.run();}
  });tabs.setOnCheckedChangeListener((g,id)->render.run());searchGroup.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});
  AlertDialog dlg=styledDialog().setTitle(ix<0?"新增分组":"编辑分组").setView(body).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dlg.setOnShowListener(d->dlg.getButton(-1).setOnClickListener(v->{try{String mode=Store.FILTERS[choice.getSelectedItemPosition()];Store.saveGroup(this,ix,name.getText().toString(),mode,picked);if("everything".equals(mode))toast("已保存实验性 everything 策略，执行可能失败");dlg.dismiss();reload();}catch(Exception e){toast(e.getMessage());}}));dlg.show();
 }

 private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
 private void showHistory(){if(historyList==null)return;JSONArray h=Store.history(this);SimpleDateFormat fmt=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());historyList.setAdapter(adapter(h.length(),i->{JSONObject obj=h.optJSONObject(i);if(obj==null)return smallLabel("无效记录");LinearLayout row=vertical();row.setPadding(dp(9),dp(6),dp(9),dp(6));row.setBackground(clickShape(Color.WHITE,BORDER,14));TextView title=text(fmt.format(new Date(obj.optLong("time")))+" · "+obj.optString("origin","旧版记录")+" · "+obj.optString("scope",obj.optString("type")),13);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,0);row.addView(title);TextView info=smallLabel("目标 "+obj.optInt("requested")+" · 已处理 "+obj.optInt("processed")+" · 成功 "+obj.optInt("success")+" · 未处理 "+obj.optInt("pending")+" · "+(obj.optLong("duration")/1000)+"秒");info.setPadding(0,0,0,0);row.addView(info);
 LinearLayout resultCounts=new LinearLayout(this);resultCounts.setGravity(Gravity.CENTER_VERTICAL);row.addView(resultCounts);
 TextView failCount=smallLabel("失败 "+obj.optInt("failed"));failCount.setTextColor(0xffC44747);failCount.setPadding(0,0,dp(18),0);resultCounts.addView(failCount);
 TextView skipCount=smallLabel("跳过 "+obj.optInt("skipped"));skipCount.setTextColor(0xffBA721E);skipCount.setPadding(0,0,0,0);resultCounts.addView(skipCount);LinearLayout box=vertical();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(2),0,dp(3));box.addView(row,lp);row.setOnClickListener(v->showTaskDetail(obj));return box;}));}
 private void showTaskDetail(JSONObject record){JSONArray entries=record.optJSONArray("details");if(entries==null||entries.length()==0){styledDialog().setTitle("任务详情").setMessage("没有可选择的执行项").setPositiveButton("关闭",null).show();return;}Set<String> picked=new HashSet<>();LinearLayout body=vertical();body.setPadding(dp(18),0,dp(18),0);TextView provenance=smallLabel("来源："+record.optString("origin","旧版记录")+" · 操作："+record.optString("scope",record.optString("type"))+"\n目标："+record.optInt("requested")+" · 已处理："+record.optInt("processed")+" · 未处理："+record.optInt("pending")+"\n执行结果保存在记录中；ART 实际状态以最近一次扫描快照为准");body.addView(provenance);TextView counter=text("已选 0 / "+entries.length(),14);body.addView(counter);
  Spinner resultFilter=styledSpinner();String[] filterLabels={"全部结果","命令成功","命令失败","已达标跳过","未处理 / 已取消","ART 未达标","ART 未知"};resultFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filterLabels));body.addView(resultFilter,new LinearLayout.LayoutParams(-1,dp(44)));CheckBox selectAll=styledCheckBox();selectAll.setText("全选任务中的包名");body.addView(selectAll);ScrollView scroll=new ScrollView(this);LinearLayout rows=vertical();scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(350)));
  List<CheckBox> checks=new ArrayList<>();for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;String pkg=e.optString("pkg");CheckBox cb=styledCheckBox();String st=e.optString("status",e.optBoolean("ok")?"success":"failed");String cn="success".equals(st)?"✓ 成功 ":("failed".equals(st)?"✗ 失败 ":"skipped".equals(st)?"— 已达标跳过 ":"deferred".equals(st)?"◷ 待重试 ":"pending".equals(st)?"… 未处理 ":"cancelled".equals(st)?"■ 已取消 ":"? 不可见 ");cb.setText(cn+pkg+"（"+e.optString("mode")+"）\nART："+e.optString("beforeArt","未知")+" → "+e.optString("afterArt","未验证")+" · "+(e.has("artMatch")?(e.optBoolean("artMatch")?"已达标":"未达标或未知"):"旧记录")+"\n"+e.optString("output"));cb.setTextSize(12);rows.addView(cb);checks.add(cb);cb.setOnCheckedChangeListener((v,selected)->{if(selected)picked.add(pkg);else picked.remove(pkg);counter.setText("已选 "+picked.size()+" / "+checks.size());});}
  resultFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){for(int j=0;j<checks.size();j++){JSONObject detail=entries.optJSONObject(j);if(detail==null)continue;String st=detail.optString("status",detail.optBoolean("ok")?"success":"failed");boolean visible=position==0||(position==1&&"success".equals(st))||(position==2&&"failed".equals(st))||(position==3&&"skipped".equals(st))||(position==4&&("pending".equals(st)||"cancelled".equals(st)))||(position==5&&detail.has("artMatch")&&detail.optBoolean("artKnown")&&!detail.optBoolean("artMatch"))||(position==6&&detail.has("artKnown")&&!detail.optBoolean("artKnown"));checks.get(j).setVisibility(visible?View.VISIBLE:View.GONE);}}});
  selectAll.setOnCheckedChangeListener((v,all)->{for(CheckBox cb:checks)if(cb.getVisibility()==View.VISIBLE)cb.setChecked(all);});AlertDialog dlg=styledDialog().setTitle("任务详情").setView(body).setNegativeButton("关闭",null).setPositiveButton("重置选中应用",null).create();dlg.setOnShowListener(d->dlg.getButton(-1).setOnClickListener(v->{if(picked.isEmpty()){toast("请先选择包名");return;}dlg.dismiss();confirmReset(new ArrayList<>(picked));}));dlg.show();}
 private List<String> installedTargetSnapshot(){List<String> snapshot=new ArrayList<>();for(PackageInfo p:Store.installed(this))snapshot.add(p.packageName);return snapshot;}
 private String nextScope="选中应用";
 private void startCompileWithMode(boolean incremental,List<String> targets,String scope,String mode){
 if(blocked())return;
 if(!Store.validMode(mode)){toast("不支持的编译模式");return;}
 List<String> frozen=new ArrayList<>(new LinkedHashSet<>(targets));
 if(frozen.isEmpty()){toast("没有可处理的应用");return;}
 if(!Engine.reserveTask()){taskBlocked();return;}
 launchCompileService(frozen,false,incremental,scope,mode);
}
 private void start(boolean incremental,List<String> targets){if(blocked())return;final List<String> frozenTargets=targets==null?installedTargetSnapshot():new ArrayList<>(new LinkedHashSet<>(targets));if(!Engine.reserveTask()){taskBlocked();return;}toast("编译任务正在启动");String scope=nextScope;nextScope="选中应用";launchCompileService(frozenTargets,false,incremental,scope);}
 private void launchCompileService(List<String> targets,boolean reset,boolean incremental,String scope){
 launchCompileService(targets,reset,incremental,scope,null);
}
 private void launchCompileService(List<String> targets,boolean reset,boolean incremental,String scope,String overrideMode){
  Intent intent=new Intent(this,CompileService.class).setAction(CompileService.ACTION_RUN);
  intent.putStringArrayListExtra(CompileService.EXTRA_PACKAGES,new ArrayList<>(targets));
  intent.putExtra(CompileService.EXTRA_RESET,reset);
  intent.putExtra(CompileService.EXTRA_INCREMENTAL,incremental);
  intent.putExtra(CompileService.EXTRA_SCOPE,scope);if(overrideMode!=null)intent.putExtra(CompileService.EXTRA_MODE,overrideMode);
  try{startForegroundService(intent);updateTaskState();}
  catch(Exception e){Engine.releaseReserved();TaskRuntime.abort("前台服务启动失败");toast("前台服务启动失败："+e.getMessage());updateTaskState();}
 }
 private boolean isFailureLog(String line){
  String v=line.toLowerCase(java.util.Locale.ROOT);
  return line.contains("失败")||line.contains("异常")||line.contains("不可用")||line.contains("未能启动")||line.contains("Root 未授权")||v.contains("error:")||v.contains("exception")||v.contains("failure");
 }
 private void refreshLogs(){if(logsList==null)return;long revision=TaskRuntime.revision();if(revision==logRevision)return;List<String> rows=TaskRuntime.logs();int oldSize=visibleLogs.size();if(logRevision>=0&&revision-logRevision==1&&rows.size()>=oldSize&&oldSize>0){visibleLogs=rows;logsList.getAdapter().notifyItemInserted(0);if(rows.size()==oldSize)logsList.getAdapter().notifyItemRemoved(rows.size());}else{visibleLogs=rows;logsList.getAdapter().notifyDataSetChanged();}logRevision=revision;if(followLogs&&!rows.isEmpty())logsList.scrollToPosition(0);}
 private void append(String s){TaskRuntime.line(s);ui.post(this::refreshLogs);}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override protected void onDestroy(){taskHandler.removeCallbacks(taskPulse);pool.shutdown();super.onDestroy();}
}
