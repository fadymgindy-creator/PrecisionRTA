package app.precisionrta.v2;



import android.Manifest;

import android.app.*;

import android.content.*;

import android.content.pm.PackageManager;

import android.content.res.Configuration;

import android.graphics.Color;

import android.media.*;

import android.net.Uri;

import android.os.*;

import android.provider.Settings;

import android.view.*;

import android.widget.*;

import java.io.*;

import java.text.SimpleDateFormat;

import java.util.*;



public class MainActivity extends Activity implements AnalyzerEngine.Listener {

    private static final int REQ_MIC=41,REQ_PHONE_CAL=42,REQ_REF_CAL=43;

    private AnalyzerEngine engine; private RtaView graph; private TextView status,subhead; private LinearLayout controls; private Button freezeBtn,modeBtn,measureBtn,peakBtn,measureViewBtn,calBtn,referenceCalBtn;

    private CalibrationProfile loadedCal,referenceCal; private double[] autoRefHz,autoRefDb,verifyRefHz,verifyRefDb,lastRawHz,lastRawDb; private String lastInput="";private int lastSampleRate=0;

    private final List<MeasurementTrace> traces=new ArrayList<MeasurementTrace>();private MeasurementAccumulator accumulator;private boolean measuring=false,measureShowLive=true;private int measurementNumber=1;

    private String projectName="Precision RTA",sessionName="";private double splOffset=0;private boolean splCalibrated=false;private double normLo=500,normHi=2000;

    private Uri pendingPhoneCalUri,pendingRefCalUri;

    private boolean recoveryPending=false;private Button controlsToggle;private boolean foreground=false,calBusy=false;private AnalyzerEngine.Frame lastFrame,measurementFrame;private long lastFrameTime;

    private String splPath="",caseNotes="not recorded",measurementOrientation="",measurementCase="";

    private final Handler ui=new Handler(Looper.getMainLooper());

    private void postIfForeground(Runnable r){ui.post(()->{if(foreground&&!isFinishing())r.run();});}

    private void postCalibration(Runnable r){postIfForeground(()->{if(calBusy)r.run();});}
    private void resumeAfterCalibration(){calBusy=false;startAnalyzer();}

    private String pathFor(AnalyzerEngine.Frame f){return f.input+"|"+(f.calibration==null?"none":f.calibration.fingerprint())+"|"+getOrientationLabel()+"|"+caseNotes+"|"+SpectrumProcessor.UNITS;}

    private void invalidateSpl(){splCalibrated=false;getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("spl_calibrated",false).apply();graph.setSplReady(false);}





    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(8,10,13));getWindow().setNavigationBarColor(Color.rgb(8,10,13));engine=new AnalyzerEngine(this,this);loadedCal=CalibrationProfile.load(this);referenceCal=CalibrationStore.loadReference(this);if(loadedCal!=null&&getSharedPreferences("rta_v2",MODE_PRIVATE).getBoolean("cal_enabled",true))engine.setCalibration(loadedCal);loadPrefs();buildUi();foreground=true;if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);else startAnalyzer();recoverTempIfAny();}



    private void loadPrefs(){android.content.SharedPreferences sp=getSharedPreferences("rta_v2",MODE_PRIVATE);measureShowLive=sp.getBoolean("measure_show_live",true);normLo=getD(sp,"norm_lo",500);normHi=getD(sp,"norm_hi",2000);splOffset=getD(sp,"spl_offset",0);splCalibrated=sp.getBoolean("spl_calibrated",false);projectName=sp.getString("project","Precision RTA");sessionName=sp.getString("session",defaultSessionName());splPath=sp.getString("spl_path","");caseNotes=sp.getString("case_notes","not recorded");}

    private static double getD(android.content.SharedPreferences s,String k,double d){return s.contains(k)?Double.longBitsToDouble(s.getLong(k,0)):d;}private void putD(String k,double v){getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putLong(k,Double.doubleToRawLongBits(v)).apply();}

    private String defaultSessionName(){return new SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(new Date());}



    private void buildUi(){

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(7),dp(5),dp(7),dp(4));root.setBackgroundColor(Color.rgb(8,10,13));

        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("PRECISION RTA 2");title.setTextColor(Color.WHITE);title.setTextSize(18);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(32),1));Button toggle=button("HIDE CONTROLS");controlsToggle=toggle;titleRow.addView(toggle,new LinearLayout.LayoutParams(dp(120),dp(34)));root.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(36)));

        subhead=new TextView(this);subhead.setText("Measurement-first car-audio analyzer · tap a graph header to maximize · double-tap graph to restore saved preset");subhead.setTextColor(Color.rgb(150,165,176));subhead.setTextSize(9.5f);root.addView(subhead,new LinearLayout.LayoutParams(-1,dp(20)));

        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);root.addView(controls,new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row1=new LinearLayout(this);freezeBtn=button("FREEZE");modeBtn=button("REL");measureBtn=button("MEASURE");peakBtn=button("PEAK OFF");Button graphBtn=button("GRAPH SETTINGS");measureViewBtn=button(measureShowLive?"VIEW LIVE+AVG":"VIEW MEASURE ONLY");for(Button x:new Button[]{freezeBtn,modeBtn,measureBtn,peakBtn,graphBtn,measureViewBtn})row1.addView(x);controls.addView(scroll(row1),new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout row2=new LinearLayout(this);Button inputBtn=button("INPUT");calBtn=button(calLabel());referenceCalBtn=button(referenceCal==null?"REF CAL OFF":"REF CAL LOADED");Button normBtn=button("NORMALIZE");Button splBtn=button("SPL CAL");Button tracesBtn=button("TRACES");Button spatialBtn=button("SPATIAL AVG");Button sessionBtn=button("SESSION");Button resetBtn=button("FACTORY GRAPH");for(Button x:new Button[]{inputBtn,calBtn,referenceCalBtn,normBtn,splBtn,tracesBtn,spatialBtn,sessionBtn,resetBtn})row2.addView(x);controls.addView(scroll(row2),new LinearLayout.LayoutParams(-1,dp(48)));

        graph=new RtaView(this);graph.setNormalization(normLo,normHi);graph.setSplOffset(splOffset);graph.setSplReady(splCalibrated);graph.setTraces(traces);graph.setConfigListener(new RtaView.ConfigListener(){public void onConfigChanged(){if(!measuring)engine.setBaseFraction(graph.getMaxFraction());if(!traces.isEmpty())saveTemp();}});root.addView(graph,new LinearLayout.LayoutParams(-1,0,1));

        status=new TextView(this);status.setText("Initializing microphone…");status.setTextColor(Color.rgb(170,185,195));status.setTextSize(9.5f);status.setGravity(Gravity.CENTER_VERTICAL);root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));setContentView(root);

        status.setOnClickListener(v->{String detail=lastFrame==null?"No current capture":lastFrame.input+"\n"+lastFrame.processing+"\n"+lastFrame.spectrum.invalidReason+"\nBand powers are integrated RMS levels. Digital full-scale sine = -3.01 dBFS RMS. Corrected digital levels are not raw dBFS. SPL is a user-referenced estimate.\n1/48 detail below "+String.format(Locale.US,"%.1f",SpectrumProcessor.resolutionLimit(lastFrame.spectrum.sampleRate,lastFrame.spectrum.fftSize,48))+" Hz is limited by FFT leakage.\nCalibration: "+(lastFrame.calibration==null?"none":lastFrame.calibration.name)+"\nCase/orientation: "+caseNotes+" / "+getOrientationLabel();new AlertDialog.Builder(this).setTitle("Capture details and limits").setMessage(detail).setPositiveButton("OK",null).show();});

        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){controls.setVisibility(View.GONE);subhead.setVisibility(View.GONE);toggle.setText("SHOW CONTROLS");}



        toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){boolean show=controls.getVisibility()!=View.VISIBLE;controls.setVisibility(show?View.VISIBLE:View.GONE);subhead.setVisibility(show?View.VISIBLE:View.GONE);((Button)v).setText(show?"HIDE CONTROLS":"SHOW CONTROLS");}});

        freezeBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(measuring){toast("Stop the measurement before freezing.");return;}if(!engine.isRunning()){startAnalyzer();return;}engine.setFreeze(!engine.isFrozen());freezeBtn.setText(engine.isFrozen()?"RESUME":"FREEZE");}});

        modeBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){cycleMode();}});

        measureBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(measuring)stopMeasurement();else startMeasurement();}});

        peakBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){graph.setPeakHold(!graph.isPeakHold());peakBtn.setText(graph.isPeakHold()?"PEAK ON":"PEAK OFF");}});

        graphBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(measuring){toast("Stop the measurement before changing graph resolution.");return;}showGraphSettings(graph.getSelectedPanel());}});

        measureViewBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){measureShowLive=!measureShowLive;getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("measure_show_live",measureShowLive).apply();measureViewBtn.setText(measureShowLive?"VIEW LIVE+AVG":"VIEW MEASURE ONLY");if(measuring)graph.setShowLive(measureShowLive);}});

        inputBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showInputPicker();}});

        calBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showCalibrationMenu();}});

        referenceCalBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showReferenceCalibrationMenu(referenceCalBtn);}});

        normBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showNormalizationDialog();}});

        splBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showSplCalibration();}});

        tracesBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showTraceManager();}});

        spatialBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){createSpatialAverage();}});

        sessionBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showSessionMenu();}});

        resetBtn.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(measuring){toast("Stop the measurement before factory-resetting a graph.");return;}int i=graph.getSelectedPanel();new AlertDialog.Builder(MainActivity.this).setTitle("Factory reset Window "+(i+1)+"?").setMessage("This replaces its saved preset with the original V2 default.").setNegativeButton("Cancel",null).setPositiveButton("Reset",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){graph.factoryResetPanel(graph.getSelectedPanel());engine.setBaseFraction(graph.getMaxFraction());}}).show();}});

    }

    private ScrollView form(LinearLayout content){ScrollView view=new ScrollView(this);view.addView(content);return view;}
    private HorizontalScrollView scroll(LinearLayout l){HorizontalScrollView s=new HorizontalScrollView(this);s.setHorizontalScrollBarEnabled(false);s.addView(l);return s;}

    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(9.5f);b.setAllCaps(false);b.setMinWidth(dp(88));b.setPadding(dp(7),0,dp(7),0);return b;}private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}



    private void cycleMode(){int m=graph.getDisplayMode();if(m==RtaView.MODE_REL)m=splCalibrated?RtaView.MODE_SPL:RtaView.MODE_DBFS;else if(m==RtaView.MODE_SPL)m=RtaView.MODE_DBFS;else m=RtaView.MODE_REL;graph.setDisplayMode(m);modeBtn.setText(m==RtaView.MODE_REL?"REL":m==RtaView.MODE_SPL?"SPL":"dBFS");}



    private void showGraphSettings(final int idx){final GraphConfig g=graph.getConfig(idx).copy();LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(4),dp(18),0);final EditText fmin=num("Frequency minimum Hz",g.fMin),fmax=num("Frequency maximum Hz",g.fMax),ymin=num("Y minimum dB",g.yMin),ymax=num("Y maximum dB",g.yMax);l.addView(label("Frequency minimum (Hz)"));l.addView(fmin);l.addView(label("Frequency maximum (Hz)"));l.addView(fmax);l.addView(label("Level minimum (dB)"));l.addView(ymin);l.addView(label("Level maximum (dB)"));l.addView(ymax);l.addView(label("Fractional-octave smoothing"));final Spinner frac=spinner(new String[]{"1/3 octave","1/6 octave","1/12 octave","1/24 octave","1/48 octave"});final int[] fracs={3,6,12,24,48};frac.setSelection(indexOf(fracs,g.fraction));l.addView(frac);l.addView(label("Temporal averaging"));final Spinner avg=spinner(new String[]{"Fast (0.15 s)","Medium (0.5 s)","Slow (1.5 s)","Very slow (4 s)"});final double[] av={.15,.5,1.5,4};avg.setSelection(nearest(av,g.avgTau));l.addView(avg);l.addView(label("Y-axis division"));final Spinner grid=spinner(new String[]{"1 dB","2 dB","3 dB","5 dB","10 dB"});final int[] grids={1,2,3,5,10};grid.setSelection(indexOf(grids,g.gridDb));l.addView(grid);final CheckBox minor=new CheckBox(this);minor.setText("Minor grid lines");minor.setChecked(g.minorGrid);l.addView(minor);final CheckBox labels=new CheckBox(this);labels.setText("Y-axis labels");labels.setChecked(g.labels);l.addView(labels);

        final AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Window "+(idx+1)+" settings").setView(form(l)).setNegativeButton("Cancel",null).setNeutralButton("Save preset",null).setPositiveButton("Apply",null).create();dlg.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){View.OnClickListener apply=new View.OnClickListener(){public void onClick(View v){try{g.fMin=Double.parseDouble(fmin.getText().toString());g.fMax=Double.parseDouble(fmax.getText().toString());g.yMin=Double.parseDouble(ymin.getText().toString());g.yMax=Double.parseDouble(ymax.getText().toString());g.fraction=fracs[frac.getSelectedItemPosition()];g.avgTau=av[avg.getSelectedItemPosition()];g.gridDb=grids[grid.getSelectedItemPosition()];g.minorGrid=minor.isChecked();g.labels=labels.isChecked();g.sanitize();graph.applyConfig(idx,g);if(v==dlg.getButton(AlertDialog.BUTTON_NEUTRAL)){graph.savePanel(idx);toast("Window "+(idx+1)+" preset saved.");}dlg.dismiss();}catch(Exception e){toast("Check the numeric ranges.");}}};dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(apply);dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(apply);}});dlg.show();}

    private EditText num(String hint,double v){EditText e=new EditText(this);e.setHint(hint);e.setText(v==Math.rint(v)?String.format(Locale.US,"%.0f",v):String.format(Locale.US,"%.2f",v));e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);return e;}private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.LTGRAY);t.setPadding(0,dp(8),0,0);return t;}private Spinner spinner(String[] a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;}private int indexOf(int[] a,int v){for(int i=0;i<a.length;i++)if(a[i]==v)return i;return 0;}private int nearest(double[] a,double v){int b=0;double q=1e9;for(int i=0;i<a.length;i++){double d=Math.abs(a[i]-v);if(d<q){q=d;b=i;}}return b;}



    private void startMeasurement(){

        if(calBusy||!engine.isRunning()||lastFrame==null){toast("Wait for a live input first.");return;}

        measuring=true;accumulator=new MeasurementAccumulator(7.5);measurementFrame=null;measurementOrientation=getOrientationLabel();measurementCase=caseNotes;

        graph.clearLive();engine.beginMeasurement();measureBtn.setText("STOP + KEEP");freezeBtn.setText("FREEZE");graph.setShowLive(measureShowLive);graph.setPreview(null);

        toast("Capturing fresh 1/48 base data. First complete window takes about 5.5 seconds; stop keeps only complete windows.");

    }

    private void stopMeasurement(){stopMeasurement(true);}

    private void stopMeasurement(boolean restart){

        measuring=false;engine.endMeasurement();MeasurementTrace t=accumulator==null?null:accumulator.trace("Measurement "+measurementNumber++);

        engine.setBaseFraction(graph.getMaxFraction());measureBtn.setText("MEASURE");graph.setShowLive(true);graph.setPreview(null);graph.clearLive();

        if(t!=null){fillTraceMetadata(t);traces.add(t);graph.setTraces(traces);saveTemp();toast(String.format(Locale.US,"Kept %.1f s; rejected %.1f s. Partial final window was not saved.",t.acceptedSeconds,t.rejectedSeconds));}else toast("No complete usable FFT windows were captured.");

        accumulator=null;measurementFrame=null;if(restart&&foreground)startAnalyzer();

    }

    private void fillTraceMetadata(MeasurementTrace t){

        AnalyzerEngine.Frame f=measurementFrame!=null?measurementFrame:lastFrame;if(f==null)return;

        t.inputName=f.input;t.sampleRate=f.spectrum.sampleRate;t.fftSize=f.spectrum.fftSize;t.calibrationName=f.calibration==null?"None":f.calibration.name;t.calibrationId=f.calibration==null?"":f.calibration.fingerprint();t.processing=f.processing;

        t.orientation=measurementFrame!=null?measurementOrientation:getOrientationLabel();t.caseNotes=measurementFrame!=null?measurementCase:caseNotes;

        t.splCalibrated=splCalibrated&&splPath.equals(pathFor(f));t.splOffset=splOffset;

        try{if(f.calibration!=null)t.calibrationSnapshot=f.calibration.toJson();}catch(Exception e){throw new IllegalStateException(e);}

    }

    private GraphConfig[] graphCopies(){GraphConfig[] g=new GraphConfig[3];for(int i=0;i<3;i++)g[i]=graph.getConfig(i).copy();return g;}

    private void saveTemp(){if(recoveryPending)return;try{List<MeasurementTrace> saved=new ArrayList<>(traces);if(measuring&&accumulator!=null){MeasurementTrace t=accumulator.trace("Interrupted measurement");if(t!=null){fillTraceMetadata(t);saved.add(t);}}SessionStore.save(this,projectName,sessionName,saved,graphCopies(),normLo,normHi,graph.getDisplayMode(),true);}catch(Exception e){status.setText("Recovery save failed: "+e.getMessage());}}



    private void showTraceManager(){if(traces.isEmpty()){toast("No saved measurements in the current session.");return;}String[] menu={"Show / hide traces","Rename trace","Delete trace","Clear all traces"};new AlertDialog.Builder(this).setTitle("Trace manager").setItems(menu,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==0)showTraceVisibility();else if(which==1)chooseTraceToRename();else if(which==2)chooseTraceToDelete();else new AlertDialog.Builder(MainActivity.this).setTitle("Clear all traces?").setNegativeButton("Cancel",null).setPositiveButton("Clear",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){traces.clear();measurementNumber=1;graph.setTraces(traces);SessionStore.clearTemp(MainActivity.this);}}).show();}}).show();}

    private String[] traceNames(){String[] n=new String[traces.size()];for(int i=0;i<n.length;i++)n[i]=traces.get(i).name;return n;}

    private void showTraceVisibility(){String[] names=new String[traces.size()];boolean[] ck=new boolean[traces.size()];for(int i=0;i<traces.size();i++){MeasurementTrace t=traces.get(i);names[i]=t.name+"  ["+t.acceptedFrames+" kept / "+t.rejectedFrames+" rejected]";ck[i]=t.visible;}new AlertDialog.Builder(this).setTitle("Visible traces").setMultiChoiceItems(names,ck,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int which,boolean isChecked){traces.get(which).visible=isChecked;graph.setTraces(traces);saveTemp();}}).setPositiveButton("Done",null).show();}

    private void chooseTraceToRename(){final String[] n=traceNames();new AlertDialog.Builder(this).setTitle("Rename trace").setItems(n,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,final int which){final EditText e=new EditText(MainActivity.this);e.setText(traces.get(which).name);new AlertDialog.Builder(MainActivity.this).setTitle("Trace name").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){String x=e.getText().toString().trim();if(x.length()>0){traces.get(which).name=x;graph.setTraces(traces);saveTemp();}}}).show();}}).show();}

    private void chooseTraceToDelete(){final String[] n=traceNames();new AlertDialog.Builder(this).setTitle("Delete trace").setItems(n,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,final int which){new AlertDialog.Builder(MainActivity.this).setTitle("Delete "+traces.get(which).name+"?").setNegativeButton("Cancel",null).setPositiveButton("Delete",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){traces.remove(which);graph.setTraces(traces);recomputeMeasurementNumber();saveTemp();}}).show();}}).show();}

    private void recomputeMeasurementNumber(){int max=0;for(MeasurementTrace t:traces)if(t.name.startsWith("Measurement "))try{max=Math.max(max,Integer.parseInt(t.name.substring(12).trim()));}catch(Exception ignored){}measurementNumber=max+1;}

    private void createSpatialAverage(){final List<MeasurementTrace> candidates=new ArrayList<MeasurementTrace>();for(MeasurementTrace t:traces)if(!t.spatial)candidates.add(t);if(candidates.size()<2){toast("Capture at least two measurements first.");return;}final String[] names=new String[candidates.size()];final boolean[] selected=new boolean[candidates.size()];for(int i=0;i<candidates.size();i++){names[i]=candidates.get(i).name;selected[i]=candidates.get(i).visible;}new AlertDialog.Builder(this).setTitle("Select measurements to spatially average").setMultiChoiceItems(names,selected,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int which,boolean checked){selected[which]=checked;}}).setNegativeButton("Cancel",null).setPositiveButton("Create average",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){List<MeasurementTrace> src=new ArrayList<MeasurementTrace>();for(int i=0;i<candidates.size();i++)if(selected[i])src.add(candidates.get(i));if(src.size()<2){toast("Select at least two measurements.");return;}buildSpatialAverage(src);}}).show();}

    private void buildSpatialAverage(List<MeasurementTrace> src){try{MeasurementTrace a=MeasurementTrace.spatialAverage(src);traces.add(a);graph.setTraces(traces);saveTemp();toast("Spatial average created; source traces retained.");}catch(Exception e){new AlertDialog.Builder(this).setTitle("Cannot combine these measurements").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}

    private double[] resample(double[] sh,double[] sd,double[] outH){double[] o=new double[outH.length];for(int i=0;i<o.length;i++){double f=outH[i];if(f<=sh[0])o[i]=sd[0];else if(f>=sh[sh.length-1])o[i]=sd[sd.length-1];else{int lo=0,hi=sh.length-1;while(hi-lo>1){int m=(lo+hi)>>>1;if(sh[m]<=f)lo=m;else hi=m;}double q=(Math.log(f)-Math.log(sh[lo]))/(Math.log(sh[hi])-Math.log(sh[lo]));o[i]=sd[lo]+q*(sd[hi]-sd[lo]);}}return o;}



    private void showNormalizationDialog(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),0,dp(18),0);final EditText lo=num("Reference low Hz",normLo),hi=num("Reference high Hz",normHi);l.addView(label("Reference low frequency (Hz)"));l.addView(lo);l.addView(label("Reference high frequency (Hz)"));l.addView(hi);new AlertDialog.Builder(this).setTitle("Relative-response normalization").setMessage("Relative mode subtracts the energy-average level in this frequency range. Stored measurements remain unchanged, so this can be changed later.").setView(form(l)).setNegativeButton("Cancel",null).setPositiveButton("Apply",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{double a=Double.parseDouble(lo.getText().toString()),b=Double.parseDouble(hi.getText().toString());if(!SpectrumMath.isFinite(a)||!SpectrumMath.isFinite(b)||a<20||b<=a||b>20000)throw new Exception();normLo=a;normHi=b;graph.setNormalization(a,b);putD("norm_lo",a);putD("norm_hi",b);}catch(Exception e){toast("Invalid normalization range.");}}}).show();}

    private void showSplCalibration(){

        if(measuring){toast("Stop the measurement before setting SPL reference.");return;}

        if(lastFrame==null){toast("Wait for a live spectrum first.");return;}final EditText e=num("Reference SPL at 1 kHz",75);

        new AlertDialog.Builder(this).setTitle("SPL estimate: one-point reference").setMessage("Play a steady 1 kHz tone. Enter the meter reading at the same position, then tap Set while playback continues. This estimates unweighted band SPL for this input, calibration, orientation and case only. It does not certify microphone accuracy.").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Set reference",(d,w)->{

            try{double real=Double.parseDouble(e.getText().toString());AnalyzerEngine.Frame f=lastFrame;if(!SpectrumMath.isFinite(real)||real<20||real>140||f==null||SystemClock.elapsedRealtime()-lastFrameTime>10000||!engine.isRunning()||engine.isFrozen()||!f.spectrum.invalidReason.isEmpty())throw new IllegalArgumentException("Use a current, unclipped live tone");

                double tone=0;for(int i=0;i<f.spectrum.hz.length;i++)if(f.spectrum.hz[i]>=900&&f.spectrum.hz[i]<=1100)tone+=SpectrumMath.dbToPower(f.spectrum.db[i]);if(tone/f.spectrum.correctedTotalPower<.90)throw new IllegalArgumentException("A steady 1 kHz tone must dominate the input");

                splOffset=real-SpectrumMath.powerToDb(f.spectrum.correctedTotalPower);splPath=pathFor(f);splCalibrated=true;graph.setSplOffset(splOffset);graph.setSplReady(true);putD("spl_offset",splOffset);getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("spl_calibrated",true).putString("spl_path",splPath).apply();toast("SPL estimate reference saved for this setup.");

            }catch(Exception x){toast(x.getMessage());}

        }).show();

    }



    private void showInputPicker(){

        if(measuring||calBusy){toast("Stop measurement/calibration before changing input.");return;}AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);final AudioDeviceInfo[] dev=am==null?new AudioDeviceInfo[0]:am.getDevices(AudioManager.GET_DEVICES_INPUTS);String[] names=new String[dev.length+2];names[0]="Android default (actual route is checked)";for(int i=0;i<dev.length;i++)names[i+1]=dev[i].getProductName()+" — "+AnalyzerEngine.typeName(dev[i].getType());names[names.length-1]="Case / microphone setup notes";

        new AlertDialog.Builder(this).setTitle("Measurement input").setItems(names,(d,which)->{if(which==names.length-1){EditText notes=new EditText(this);notes.setText(caseNotes);new AlertDialog.Builder(this).setTitle("Case on/off and setup notes").setView(notes).setPositiveButton("Save",(x,w)->{caseNotes=notes.getText().toString();getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putString("case_notes",caseNotes).apply();invalidateSpl();}).show();return;}engine.stop();engine.setPreferredDevice(which==0?null:dev[which-1]);lastFrame=null;graph.clearLive();invalidateSpl();startAnalyzer();}).show();

    }



    private String calLabel(){return loadedCal==null?"PHONE CAL OFF":engine!=null&&engine.getCalibration()!=null?"PHONE CAL ON":"PHONE CAL OFF";}

    private void showCalibrationMenu(){if(measuring||calBusy){toast("Stop measurement/calibration before changing calibration.");return;}String[] options={engine.getCalibration()==null?"Enable active phone calibration":"Disable phone calibration","Import phone .cal/.txt","Choose saved phone profile","Reference-match Auto Cal","Verify active calibration"};new AlertDialog.Builder(this).setTitle("Phone microphone calibration").setItems(options,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w==0){if(engine.getCalibration()!=null){getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("cal_enabled",false).apply();engine.setCalibration(null);invalidateSpl();engine.restart();graph.clearLive();calBtn.setText("PHONE CAL OFF");}else if(loadedCal!=null){getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("cal_enabled",true).apply();engine.setCalibration(loadedCal);invalidateSpl();engine.restart();graph.clearLive();calBtn.setText("PHONE CAL ON");}else toast("Import or create a phone calibration first.");}else if(w==1)chooseFile(REQ_PHONE_CAL);else if(w==2)chooseSavedCalibration();else if(w==3)showAutoCalIntro();else verifyCalibration();}}).show();}

    private void chooseSavedCalibration(){final String[] n=CalibrationStore.names(this);if(n.length==0){toast("No saved calibration profiles.");return;}new AlertDialog.Builder(this).setTitle("Saved phone calibration profiles").setItems(n,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{loadedCal=CalibrationStore.load(MainActivity.this,n[w]);CalibrationStore.setActive(MainActivity.this,n[w]);getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("cal_enabled",true).apply();engine.setCalibration(loadedCal);invalidateSpl();engine.restart();graph.clearLive();calBtn.setText("PHONE CAL ON");warnCalMetadata();}catch(Exception e){toast("Could not load profile.");}}}).show();}

    private void showReferenceCalibrationMenu(final Button b){if(measuring||calBusy){toast("Stop measurement/calibration first.");return;}String[] o={referenceCal==null?"Import manufacturer reference-mic .cal":"Replace manufacturer reference-mic .cal","Clear reference-mic calibration"};new AlertDialog.Builder(this).setTitle("Reference microphone calibration").setMessage(referenceCal==null?"No manufacturer file loaded. Auto Cal can still run, but a microphone-specific factory file improves the reference.":"Loaded: "+referenceCal.name).setItems(o,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w==0)chooseFile(REQ_REF_CAL);else{AtomicStore.delete(new File(getFilesDir(),"reference-calibration.json"));referenceCal=null;b.setText("REF CAL OFF");}}}).show();}

    private void chooseFile(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,req);}

    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null||data.getData()==null)return;final Uri u=data.getData();if(req==REQ_PHONE_CAL){pendingPhoneCalUri=u;askCalConvention(false);}else if(req==REQ_REF_CAL){pendingRefCalUri=u;askCalConvention(true);}}

    private void askCalConvention(final boolean reference){new AlertDialog.Builder(this).setTitle("Calibration-file convention").setMessage("Choose how the second numeric column should be interpreted. Phase or additional columns are ignored in V2.").setItems(new String[]{"Measured mic error — subtract values (common)","Correction values — add values"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){loadCalibrationFile(reference?pendingRefCalUri:pendingPhoneCalUri,reference,which==0?-1:1);}}).show();}

    private void loadCalibrationFile(Uri u,boolean reference,double sign){try{InputStream in=getContentResolver().openInputStream(u);String name=(reference?"Reference - ":"")+(u.getLastPathSegment()==null?"Calibration":u.getLastPathSegment());CalibrationProfile p=CalibrationProfile.parse(in,name,sign);in.close();p.deviceModel=Build.MANUFACTURER+" "+Build.MODEL;p.orientation=getOrientationLabel();p.inputName=lastInput;p.sampleRate=lastSampleRate;p.caseNotes=caseNotes;if(reference){referenceCal=p;CalibrationStore.saveReference(this,p);toast("Reference mic file loaded: "+p.hz.length+" points");rebuildCalButtons();}else{loadedCal=p;p.save(this);getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("cal_enabled",true).apply();engine.setCalibration(p);invalidateSpl();graph.clearLive();if(!calBusy)engine.restart();calBtn.setText("PHONE CAL ON");toast("Phone calibration loaded: "+p.hz.length+" points");}}catch(Exception e){new AlertDialog.Builder(this).setTitle("Calibration import failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}

    private void rebuildCalButtons(){if(referenceCalBtn!=null)referenceCalBtn.setText(referenceCal==null?"REF CAL OFF":"REF CAL LOADED");}



    private void showAutoCalIntro(){calBusy=true;engine.stop();new AlertDialog.Builder(this).setTitle("Reference-match Auto Cal").setMessage("Use your calibrated measurement microphone through the USB-C audio adapter. Put its capsule immediately beside the phone microphone position and point both the same direction. Play steady pink noise from one speaker.\n\nThe reference capture will use the manufacturer .cal file if one is loaded. The process matches frequency-response shape; absolute SPL is calibrated separately.").setNegativeButton("Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setPositiveButton("Capture reference mic",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){captureReference();}}).setCancelable(false).show();}

    private void captureReference(){calBusy=true;engine.stop();final AudioDeviceInfo ext=AutoCalibrator.findExternalInput(this);if(ext==null){new AlertDialog.Builder(this).setTitle("External microphone not found").setMessage("Connect the reference microphone through the USB-C audio adapter and run Auto Cal again.").setPositiveButton("OK",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setCancelable(false).show();return;}status.setText("Auto Cal: capturing external reference for about 33 seconds…");AutoCalibrator.capture(this,ext,referenceCal,new AutoCalibrator.Callback(){public void done(final double[] h,final double[] db,final String err){postCalibration(new Runnable(){public void run(){if(err!=null){showCalError(err);return;}autoRefHz=h;autoRefDb=db;new AlertDialog.Builder(MainActivity.this).setTitle("Reference captured").setMessage("Keep the phone and speaker fixed. You may disconnect the USB-C microphone adapter now. Tap Capture phone to measure the built-in microphone.").setNegativeButton("Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setPositiveButton("Capture phone",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){capturePhoneForCal();}}).setCancelable(false).show();}});}});}

    private void capturePhoneForCal(){calBusy=true;engine.stop();final AudioDeviceInfo phone=AutoCalibrator.findBuiltIn(this);status.setText("Auto Cal: capturing built-in phone microphone…");AutoCalibrator.capture(this,phone,null,new AutoCalibrator.Callback(){public void done(final double[] h,final double[] db,final String err){postCalibration(new Runnable(){public void run(){if(err!=null){showCalError(err);return;}try{String model=Build.MANUFACTURER+" "+Build.MODEL+" auto-match "+new SimpleDateFormat("MMdd-HHmm",Locale.US).format(new Date());CalibrationProfile p=AutoCalibrator.makeProfile(model,autoRefHz,autoRefDb,h,db);p.deviceModel=Build.MANUFACTURER+" "+Build.MODEL;p.orientation=getOrientationLabel();p.inputName=AutoCalibrator.lastInput;p.sampleRate=AutoCalibrator.lastSampleRate;p.caseNotes=caseNotes;p.referenceCalibration=referenceCal==null?"None":referenceCal.name;loadedCal=p;p.save(MainActivity.this);getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putBoolean("cal_enabled",true).apply();engine.setCalibration(p);invalidateSpl();graph.clearLive();if(!calBusy)engine.restart();calBtn.setText("PHONE CAL ON");new AlertDialog.Builder(MainActivity.this).setTitle("Phone calibration created").setMessage("Saved "+p.hz.length+" correction points. The midband sensitivity offset was removed, so this profile corrects frequency-response shape only. Orientation metadata: "+p.orientation+".\n\nUse Verify Calibration for an independent second comparison.").setPositiveButton("Use it",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setCancelable(false).show();}catch(Exception e){showCalError(e.getMessage());}}});}});}

    private void showCalError(String e){new AlertDialog.Builder(this).setTitle("Calibration failed").setMessage(e).setPositiveButton("OK",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setCancelable(false).show();}



    private void verifyCalibration(){if(loadedCal==null){toast("No active phone calibration to verify.");return;}calBusy=true;engine.stop();new AlertDialog.Builder(this).setTitle("Verify calibration").setMessage("This uses a fresh reference/phone pair. Connect the external reference microphone, keep the geometry fixed, and play steady pink noise.").setNegativeButton("Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setPositiveButton("Capture reference",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){final AudioDeviceInfo ext=AutoCalibrator.findExternalInput(MainActivity.this);if(ext==null){showCalError("External reference microphone not found.");return;}AutoCalibrator.capture(MainActivity.this,ext,referenceCal,new AutoCalibrator.Callback(){public void done(final double[] h,final double[] db,final String err){postCalibration(new Runnable(){public void run(){if(err!=null){showCalError(err);return;}verifyRefHz=h;verifyRefDb=db;new AlertDialog.Builder(MainActivity.this).setTitle("Reference verification capture complete").setMessage("Keep everything fixed. Disconnect the external adapter if desired, then capture the phone.").setPositiveButton("Capture phone",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){AutoCalibrator.capture(MainActivity.this,AutoCalibrator.findBuiltIn(MainActivity.this),loadedCal,new AutoCalibrator.Callback(){public void done(final double[] ph,final double[] pd,final String er){postCalibration(new Runnable(){public void run(){if(er!=null){showCalError(er);return;}double rms=AutoCalibrator.verificationRms(verifyRefHz,verifyRefDb,ph,pd,null,50,10000);new AlertDialog.Builder(MainActivity.this).setTitle("Calibration verification").setMessage(String.format(Locale.US,"Fresh-match RMS error (1/12 octave): %.2f dB from 50 Hz to 10 kHz.\n\nLower is better. This score includes room/position changes between the two captures, so it is a practical verification metric rather than a laboratory uncertainty certificate.",rms)).setPositiveButton("Done",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){resumeAfterCalibration();}}).setCancelable(false).show();}});}});}}).setCancelable(false).show();}});}});}}).setCancelable(false).show();}



    private void showSessionMenu(){if(measuring){toast("Stop the current measurement before changing sessions.");return;}String[] o={"Save current session","Load saved session","Set project / session name","Discard current traces"};new AlertDialog.Builder(this).setTitle(projectName+" / "+sessionName).setItems(o,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w==0)saveSession();else if(w==1)loadSessionPicker();else if(w==2)setSessionNames();else{traces.clear();measurementNumber=1;graph.setTraces(traces);SessionStore.clearTemp(MainActivity.this);}}}).show();}

    private void saveSession(){try{SessionStore.save(this,projectName,sessionName,traces,graphCopies(),normLo,normHi,graph.getDisplayMode(),false);SessionStore.clearTemp(this);toast("Session saved: "+sessionName);}catch(Exception e){toast("Session save failed: "+e.getMessage());}}

    private void setSessionNames(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),0,dp(18),0);final EditText p=new EditText(this),s=new EditText(this);p.setHint("Project");p.setText(projectName);s.setHint("Session");s.setText(sessionName);l.addView(p);l.addView(s);new AlertDialog.Builder(this).setTitle("Project and session").setView(form(l)).setNegativeButton("Cancel",null).setPositiveButton("Apply",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){projectName=p.getText().toString().trim();sessionName=s.getText().toString().trim();if(projectName.length()==0)projectName="Precision RTA";if(sessionName.length()==0)sessionName=defaultSessionName();getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putString("project",projectName).putString("session",sessionName).apply();saveTemp();}}).show();}

    private void loadSessionPicker(){final File[] f=SessionStore.list(this);if(f.length==0){toast("No saved sessions.");return;}Arrays.sort(f,new Comparator<File>(){public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});String[] n=new String[f.length];for(int i=0;i<f.length;i++)n[i]=f[i].getName().replace(".json","");new AlertDialog.Builder(this).setTitle("Saved sessions").setItems(n,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{SessionStore.Loaded l=SessionStore.loadFile(f[w]);projectName=l.project;sessionName=l.session;traces.clear();traces.addAll(l.traces);recomputeMeasurementNumber();graph.setTraces(traces);restoreSessionDisplay(l);getSharedPreferences("rta_v2",MODE_PRIVATE).edit().putString("project",projectName).putString("session",sessionName).apply();saveTemp();toast("Loaded "+traces.size()+" traces.");}catch(Exception e){toast("Could not load session: "+e.getMessage());}}}).show();}

    private void recoverTempIfAny(){try{final SessionStore.Loaded l=SessionStore.loadTemp(this);if(l==null||l.traces.isEmpty())return;recoveryPending=true;new AlertDialog.Builder(this).setCancelable(false).setTitle("Recover unsaved measurement session?").setMessage(l.traces.size()+" traces were preserved from the previous app session.").setNegativeButton("Discard",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){recoveryPending=false;SessionStore.clearTemp(MainActivity.this);}}).setPositiveButton("Recover",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){recoveryPending=false;projectName=l.project;sessionName=l.session;traces.clear();traces.addAll(l.traces);recomputeMeasurementNumber();graph.setTraces(traces);restoreSessionDisplay(l);saveTemp();}}).show();}catch(Exception e){toast("Recovery failed: "+e.getMessage());}}





    private void restoreSessionDisplay(SessionStore.Loaded l){if(l.graphs!=null)for(int i=0;i<3;i++)graph.loadSessionConfig(i,l.graphs[i]);normLo=l.normLo;normHi=l.normHi;graph.setNormalization(normLo,normHi);graph.setDisplayMode(l.displayMode);modeBtn.setText(l.displayMode==RtaView.MODE_REL?"REL":l.displayMode==RtaView.MODE_SPL?"SPL":"dBFS");engine.setBaseFraction(graph.getMaxFraction());}

    private String getOrientationLabel(){int rot=getWindowManager().getDefaultDisplay().getRotation();if(rot==Surface.ROTATION_90)return "landscape left";if(rot==Surface.ROTATION_270)return "landscape right";if(rot==Surface.ROTATION_180)return "portrait inverted";return "portrait upright";}

    private void warnCalMetadata(){if(loadedCal==null)return;String now=getOrientationLabel();if(loadedCal.orientation!=null&&loadedCal.orientation.length()>0&&!loadedCal.orientation.equals(now))toast("Calibration was created in "+loadedCal.orientation+"; current orientation is "+now+".");}



    private void startAnalyzer(){if(!foreground||calBusy||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;engine.setBaseFraction(graph.getMaxFraction());engine.start();freezeBtn.setText(engine.isRunning()?"FREEZE":"RESUME");warnCalMetadata();}

    @Override public void onFrame(final AnalyzerEngine.Frame frame){postIfForeground(()->{

        if(!engine.isCurrent(frame))return;SpectrumProcessor.Result r=frame.spectrum;

        lastFrame=frame;lastFrameTime=SystemClock.elapsedRealtime();lastRawHz=r.hz;lastRawDb=r.db;lastInput=frame.input;lastSampleRate=r.sampleRate;

        if(splCalibrated&&!splPath.equals(pathFor(frame)))invalidateSpl();graph.setAcquisition(r.sampleRate,r.fftSize,frame.calibration!=null);graph.setRawSpectrum(r.hz,r.db);

        if(measuring&&frame.formal&&accumulator!=null){measurementFrame=frame;boolean kept=accumulator.add(r);MeasurementTrace preview=accumulator.trace("Measuring…");if(preview!=null){fillTraceMetadata(preview);graph.setPreview(preview);}saveTemp();status.setText(String.format(Locale.US,"MEASURING · %.1f s kept / %.1f s rejected%s · tap for input details",accumulator.acceptedSeconds(),accumulator.rejectedSeconds(),kept?"":" · clipping/dropout/transient"));}

        else{String cal=frame.calibration==null?"raw digital":"frequency corrected";String warning=r.invalidReason.isEmpty()?r.qualityWarning:r.invalidReason;if(warning.isEmpty()&&frame.calibration!=null&&(!frame.calibration.orientation.isEmpty()&&!frame.calibration.orientation.equals(getOrientationLabel())||!frame.calibration.inputName.isEmpty()&&!frame.calibration.inputName.equals(frame.input)||!frame.calibration.caseNotes.isEmpty()&&!frame.calibration.caseNotes.equals(caseNotes)))warning="CAL SETUP MISMATCH";

            status.setText(String.format(Locale.US,"%d Hz · FFT %,d · %s · %s · tap for details",r.sampleRate,r.fftSize,cal,warning.isEmpty()?"processing unverified":warning));}

    });}

    @Override public void onStatus(final String message){postIfForeground(()->{if(measuring)stopMeasurement(false);lastFrame=null;graph.clearLive();freezeBtn.setText("RESUME");status.setText(message+" · tap RESUME to retry");});}

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MIC){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startAnalyzer();else new AlertDialog.Builder(this).setTitle("Microphone permission required").setMessage("Precision RTA cannot analyze sound without microphone access.").setPositiveButton("App settings",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}}).show();}}



    @Override public void onConfigurationChanged(Configuration newConfig){super.onConfigurationChanged(newConfig);boolean landscape=newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE;controls.setVisibility(landscape?View.GONE:View.VISIBLE);subhead.setVisibility(landscape?View.GONE:View.VISIBLE);controlsToggle.setText(landscape?"SHOW CONTROLS":"HIDE CONTROLS");if(measuring)stopMeasurement();if(calBusy){AutoCalibrator.cancel();calBusy=false;startAnalyzer();toast("Calibration cancelled because orientation changed.");}invalidateSpl();warnCalMetadata();if(loadedCal!=null&&!getOrientationLabel().equals(loadedCal.orientation)){String[] names=CalibrationStore.names(this);for(String n:names){try{CalibrationProfile p=CalibrationStore.load(this,n);if(p!=null&&getOrientationLabel().equals(p.orientation)){toast("Saved calibration profile matches this orientation: "+p.name);break;}}catch(Exception ignored){}}}}

    @Override protected void onResume(){super.onResume();foreground=true;if(engine!=null&&!engine.isRunning())startAnalyzer();}

    @Override protected void onPause(){foreground=false;AutoCalibrator.cancel();calBusy=false;if(measuring)stopMeasurement(false);if(engine!=null)engine.stop();if(graph!=null)saveTemp();super.onPause();}

    @Override protected void onDestroy(){foreground=false;ui.removeCallbacksAndMessages(null);AutoCalibrator.cancel();if(engine!=null)engine.stop();super.onDestroy();}

}
