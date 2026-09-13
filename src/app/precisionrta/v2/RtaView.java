package app.precisionrta.v2;



import android.content.Context;

import android.graphics.*;

import android.view.*;

import java.util.*;



final class RtaView extends View {

    static final int MODE_REL=0,MODE_DBFS=1,MODE_SPL=2;

    interface ConfigListener{void onConfigChanged();}

    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);

    private final GraphConfig[] cfg=new GraphConfig[3],savedCfg=new GraphConfig[3];

    private final double[][] liveHz=new double[3][],liveDb=new double[3][],peakDb=new double[3][];private final long[][] peakStamp=new long[3][];private final long[] lastNs=new long[3];

    private double[] rawHz,rawDb;private List<MeasurementTrace> traces=new ArrayList<MeasurementTrace>();private MeasurementTrace preview;

    private int displayMode=MODE_REL,maximized=-1,selectedPanel=0;private double splOffset=0,normLo=500,normHi=2000;private boolean showLive=true,peakHold=false;

    private float cursorX=-1,cursorY=-1;private int cursorPanel=-1;private double cursorHz,cursorDb;private String cursorTarget="";

    private final ScaleGestureDetector scaleDetector;private final GestureDetector gestureDetector;private int scalePanel=-1;private GraphConfig scaleStart;private float startSpanX,startSpanY,scaleFocusX;

    private ConfigListener configListener;

    private boolean splReady=false,digitalCorrected=false;private int streamRate=48000,fftLength=65536;

    void setSplReady(boolean ready){splReady=ready;clearLive();}

    void setAcquisition(int sr,int n,boolean corrected){if(streamRate!=sr||fftLength!=n||digitalCorrected!=corrected)clearLive();streamRate=sr;fftLength=n;digitalCorrected=corrected;}

    void clearLive(){rawHz=null;rawDb=null;for(int i=0;i<3;i++)clearPanelAverage(i);invalidate();}

    private double[][] processed(double[] h,double[] d,GraphConfig g,double offset){

        double[][] sm=SpectrumMath.smooth(h,d,g.fraction,20,20000);

        double shift=displayMode==MODE_REL?-SpectrumMath.normalizeOffset(sm[0],sm[1],normLo,normHi):displayMode==MODE_SPL?offset:0;

        for(int i=0;i<sm[1].length;i++)sm[1][i]+=shift;return sm;

    }





    RtaView(final Context c){super(c);setBackgroundColor(Color.rgb(8,10,13));text.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL));text.setTextSize(dp(10));for(int i=0;i<3;i++){cfg[i]=GraphConfig.loadCurrent(c,i);savedCfg[i]=GraphConfig.loadPreset(c,i);}

        scaleDetector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScaleBegin(ScaleGestureDetector d){scalePanel=panelAt(d.getFocusY());if(scalePanel<0)return false;scaleStart=cfg[scalePanel].copy();startSpanX=Math.max(1,d.getCurrentSpanX());startSpanY=Math.max(1,d.getCurrentSpanY());scaleFocusX=d.getFocusX();selectedPanel=scalePanel;return true;}@Override public boolean onScale(ScaleGestureDetector d){if(scalePanel<0||scaleStart==null)return false;GraphConfig g=cfg[scalePanel];float sx=Math.max(.15f,d.getCurrentSpanX()/startSpanX),sy=Math.max(.15f,d.getCurrentSpanY()/startSpanY);RectF r=panelRect(scalePanel);float left=dp(44),right=getWidth()-dp(8);double t=Math.max(0,Math.min(1,(scaleFocusX-left)/(right-left)));double a=Math.log(scaleStart.fMin),b=Math.log(scaleStart.fMax),span=(b-a)/sx,focus=a+t*(b-a),na=focus-t*span,nb=focus+(1-t)*span;double min=Math.exp(na),max=Math.exp(nb);if(min<10){double q=10/min;min*=q;max*=q;}if(max>24000){double q=24000/max;min*=q;max*=q;}if(max/min>1.03){g.fMin=min;g.fMax=max;}double yc=(scaleStart.yMin+scaleStart.yMax)/2.0,yr=Math.max(2,(scaleStart.yMax-scaleStart.yMin)/sy);g.yMin=Math.max(-120,yc-yr/2);g.yMax=Math.min(140,yc+yr/2);if(g.yMax-g.yMin<1){g.yMin=yc-.5;g.yMax=yc+.5;}invalidate();return true;}@Override public void onScaleEnd(ScaleGestureDetector d){if(scalePanel>=0){cfg[scalePanel].sanitize();cfg[scalePanel].saveCurrent(getContext(),scalePanel);if(configListener!=null)configListener.onConfigChanged();}scalePanel=-1;scaleStart=null;}});

        gestureDetector=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){selectedPanel=Math.max(0,panelAt(e.getY()));return true;}@Override public boolean onDoubleTap(MotionEvent e){int i=panelAt(e.getY());if(i>=0){cfg[i]=savedCfg[i].copy();cfg[i].saveCurrent(getContext(),i);clearPanelAverage(i);if(configListener!=null)configListener.onConfigChanged();invalidate();}return true;}@Override public boolean onSingleTapConfirmed(MotionEvent e){int i=panelAt(e.getY());if(i<0)return true;selectedPanel=i;RectF r=panelRect(i);if(e.getY()<=r.top+dp(24)){maximized=maximized==i?-1:i;invalidate();return true;}setCursor(i,e.getX(),e.getY());performClick();return true;}});

    }

    void setConfigListener(ConfigListener l){configListener=l;}GraphConfig getConfig(int i){return cfg[i];}int getSelectedPanel(){return selectedPanel;}int getMaxFraction(){return Math.max(cfg[0].fraction,Math.max(cfg[1].fraction,cfg[2].fraction));}

    void savePanel(int i){cfg[i].sanitize();cfg[i].saveCurrent(getContext(),i);cfg[i].savePreset(getContext(),i);savedCfg[i]=cfg[i].copy();}void factoryResetPanel(int i){cfg[i]=GraphConfig.factory(i);cfg[i].saveCurrent(getContext(),i);cfg[i].savePreset(getContext(),i);savedCfg[i]=cfg[i].copy();clearPanelAverage(i);if(configListener!=null)configListener.onConfigChanged();invalidate();}

    void applyConfig(int i,GraphConfig g){g.sanitize();cfg[i]=g;cfg[i].saveCurrent(getContext(),i);clearPanelAverage(i);if(configListener!=null)configListener.onConfigChanged();invalidate();}

    void loadSessionConfig(int i,GraphConfig g){cfg[i]=g.copy();cfg[i].saveCurrent(getContext(),i);clearPanelAverage(i);if(configListener!=null)configListener.onConfigChanged();invalidate();}

    void setSpectrum(double[] h,double[] d,double[] unused,boolean rel){setRawSpectrum(h,d);}void setRawSpectrum(double[] h,double[] d){rawHz=h;rawDb=d;long now=System.nanoTime();for(int i=0;i<3;i++)updatePanel(i,h,d,now);invalidate();}

    void setDisplayMode(int m){displayMode=m;for(int i=0;i<3;i++)clearPanelAverage(i);invalidate();}int getDisplayMode(){return displayMode;}void setSplOffset(double v){splOffset=v;for(int i=0;i<3;i++)clearPanelAverage(i);}void setNormalization(double lo,double hi){normLo=lo;normHi=hi;for(int i=0;i<3;i++)clearPanelAverage(i);invalidate();}double getNormLo(){return normLo;}double getNormHi(){return normHi;}

    void setTraces(List<MeasurementTrace> t){traces=t==null?new ArrayList<MeasurementTrace>():t;invalidate();}void setPreview(MeasurementTrace t){preview=t;invalidate();}void setShowLive(boolean b){showLive=b;invalidate();}void setPeakHold(boolean b){peakHold=b;if(!b)for(int i=0;i<3;i++){peakDb[i]=null;peakStamp[i]=null;}invalidate();}boolean isPeakHold(){return peakHold;}

    private double[] displayTransform(double[] h,double[] d){double[] o=d.clone();if(displayMode==MODE_REL){double off=SpectrumMath.normalizeOffset(h,o,normLo,normHi);for(int i=0;i<o.length;i++)o[i]-=off;}else if(displayMode==MODE_SPL){for(int i=0;i<o.length;i++)o[i]+=splOffset;}return o;}

    private void updatePanel(int i,double[] h,double[] d,long now){GraphConfig g=cfg[i];double[][] sm=processed(h,d,g,splOffset);double[] nh=sm[0],nd=sm[1];if(liveHz[i]==null||!SpectrumMath.sameGrid(liveHz[i],nh)){liveHz[i]=nh;liveDb[i]=nd;peakDb[i]=null;lastNs[i]=now;return;}double dt=(now-lastNs[i])/1e9;lastNs[i]=now;double alpha=1-Math.exp(-Math.max(.001,dt)/g.avgTau);for(int k=0;k<nd.length;k++){double a=SpectrumMath.dbToPower(liveDb[i][k]),b=SpectrumMath.dbToPower(nd[k]);liveDb[i][k]=SpectrumMath.powerToDb(a+alpha*(b-a));}liveHz[i]=nh;if(peakHold){if(peakDb[i]==null||peakDb[i].length!=nd.length){peakDb[i]=liveDb[i].clone();peakStamp[i]=new long[nd.length];Arrays.fill(peakStamp[i],now);}else for(int k=0;k<nd.length;k++){if(liveDb[i][k]>=peakDb[i][k]){peakDb[i][k]=liveDb[i][k];peakStamp[i][k]=now;}else{double age=(now-peakStamp[i][k])/1e9;if(age>2)peakDb[i][k]=Math.max(liveDb[i][k],peakDb[i][k]-1.5*dt);}}}}

    private void clearPanelAverage(int i){liveHz[i]=null;liveDb[i]=null;peakDb[i]=null;peakStamp[i]=null;lastNs[i]=0;}

    private float dp(float x){return x*getResources().getDisplayMetrics().density;}

    private RectF panelRect(int i){float gap=dp(7),top=dp(4),h=getHeight();if(maximized>=0)return i==maximized?new RectF(0,top,getWidth(),h-dp(2)):new RectF(0,0,0,0);float full=(h-top-2*gap)*.50f,small=(h-top-2*gap-full)/2f;if(i==0)return new RectF(0,top,getWidth(),top+full);if(i==1)return new RectF(0,top+full+gap,getWidth(),top+full+gap+small);return new RectF(0,top+full+gap+small+gap,getWidth(),h-dp(2));}

    private int panelAt(float y){if(maximized>=0)return maximized;for(int i=0;i<3;i++){RectF r=panelRect(i);if(y>=r.top&&y<=r.bottom)return i;}return -1;}

    @Override protected void onDraw(Canvas c){super.onDraw(c);for(int i=0;i<3;i++)if(maximized<0||maximized==i)drawPanel(c,i,panelRect(i));if(cursorPanel>=0&&(maximized<0||maximized==cursorPanel))drawCursor(c);}

    private void drawPanel(Canvas c,int i,RectF r){if(r.width()<=0)return;GraphConfig g=cfg[i];p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(13,17,22));c.drawRect(r,p);float left=dp(44),right=getWidth()-dp(8),top=r.top+dp(24),bottom=r.bottom-dp(18);if(bottom<=top)return;

        double first=Math.ceil(g.yMin/g.gridDb)*g.gridDb;for(double v=first;v<=g.yMax+.001;v+=g.gridDb){float y=mapDb(v,g.yMin,g.yMax,top,bottom);p.setStrokeWidth(dp(.7f));p.setColor(Color.rgb(50,57,66));c.drawLine(left,y,right,y,p);if(g.labels){text.setTextSize(dp(8.5f));text.setColor(Color.rgb(145,155,165));c.drawText(String.format(Locale.US,"%.0f",v),dp(4),y+dp(3),text);}if(g.minorGrid&&g.gridDb>=2){double m=v+g.gridDb/2.0;if(m<g.yMax){float my=mapDb(m,g.yMin,g.yMax,top,bottom);p.setColor(Color.rgb(31,37,43));c.drawLine(left,my,right,my,p);}}}

        drawFreqGrid(c,g,left,right,top,bottom);

        text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));text.setTextSize(dp(9.5f));text.setColor(i==selectedPanel?Color.WHITE:Color.rgb(210,220,228));String head=String.format(Locale.US,"WINDOW %d   %s-%s   1/%d   AVG %s   %ddB/div    tap header: maximize",i+1,formatHz(g.fMin),formatHz(g.fMax),g.fraction,avgName(g.avgTau),g.gridDb);int headerSave=c.save();c.clipRect(left,r.top,right,r.top+dp(24));c.drawText(head,left,r.top+dp(15),text);c.restoreToCount(headerSave);

        String units=displayMode==MODE_REL?"REL":displayMode==MODE_SPL?"SPL est.":digitalCorrected?"corrected dB re FS":"band dBFS";

        double limit=SpectrumProcessor.resolutionLimit(streamRate,fftLength,g.fraction);

        text.setTextSize(dp(8));text.setColor(Color.rgb(255,180,80));c.drawText(units+(g.fMin<limit?" · detail <"+formatHz(limit)+" Hz limited":""),left,bottom-dp(3),text);text.setTypeface(Typeface.MONOSPACE);

        // saved traces first

        int[] colors={Color.rgb(255,181,71),Color.rgb(182,134,255),Color.rgb(105,220,146),Color.rgb(255,113,133),Color.rgb(231,220,110)};int ci=0;for(MeasurementTrace t:traces)if(t.visible&&(displayMode!=MODE_SPL||t.splCalibrated)){drawTrace(c,t,g,left,right,top,bottom,colors[ci++%colors.length],dp(1.35f));}

        if(preview!=null&&(displayMode!=MODE_SPL||preview.splCalibrated))drawTrace(c,preview,g,left,right,top,bottom,Color.rgb(255,235,120),dp(2.3f));

        if(showLive&&liveHz[i]!=null&&(displayMode!=MODE_SPL||splReady)){if(peakHold&&peakDb[i]!=null)drawAlreadyProcessed(c,liveHz[i],peakDb[i],g,left,right,top,bottom,Color.rgb(105,115,126),dp(1));drawAlreadyProcessed(c,liveHz[i],liveDb[i],g,left,right,top,bottom,Color.rgb(80,205,255),dp(2));}

        if(displayMode==MODE_REL&&g.yMin<=0&&g.yMax>=0){float z=mapDb(0,g.yMin,g.yMax,top,bottom);p.setColor(Color.rgb(105,145,160));p.setStrokeWidth(dp(1));c.drawLine(left,z,right,z,p);}

    }

    private void drawTrace(Canvas c,MeasurementTrace t,GraphConfig g,float left,float right,float top,float bottom,int color,float width){double[][] sm=processed(t.hz,t.db,g,t.splOffset);drawAlreadyProcessed(c,sm[0],sm[1],g,left,right,top,bottom,color,width);}

    private void drawAlreadyProcessed(Canvas c,double[] h,double[] d,GraphConfig g,float left,float right,float top,float bottom,int color,float width){int save=c.save();c.clipRect(left,top,right,bottom);Path path=new Path();boolean started=false;for(int k=0;k<h.length;k++){if(h[k]<g.fMin||h[k]>g.fMax)continue;float x=mapF(h[k],g.fMin,g.fMax,left,right),y=mapDb(d[k],g.yMin,g.yMax,top,bottom);if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawPath(path,p);c.restoreToCount(save);p.setStyle(Paint.Style.FILL);}

    private void drawFreqGrid(Canvas c,GraphConfig g,float left,float right,float top,float bottom){double[] mult={1,2,3.15,5,8};double decade=Math.pow(10,Math.floor(Math.log10(g.fMin)));float lastLabel=-999;for(double base=decade;base<=g.fMax*10;base*=10){for(double m:mult){double f=base*m;if(f<g.fMin||f>g.fMax)continue;float x=mapF(f,g.fMin,g.fMax,left,right);p.setColor(Color.rgb(34,41,48));p.setStrokeWidth(dp(.7f));c.drawLine(x,top,x,bottom,p);String s=formatHz(f);text.setTextSize(dp(8));float tw=text.measureText(s);if(x-lastLabel>tw+dp(12)){text.setColor(Color.rgb(125,136,147));c.drawText(s,x-tw/2,bottom+dp(12),text);lastLabel=x;}}if(base>1e6)break;}}

    private void drawCursor(Canvas c){RectF r=panelRect(cursorPanel);GraphConfig g=cfg[cursorPanel];float top=r.top+dp(24),bottom=r.bottom-dp(18);if(cursorHz<g.fMin||cursorHz>g.fMax)return;cursorX=mapF(cursorHz,g.fMin,g.fMax,dp(44),getWidth()-dp(8));p.setColor(Color.WHITE);p.setStrokeWidth(dp(1));c.drawLine(cursorX,top,cursorX,bottom,p);String val=formatHz(cursorHz)+"   "+String.format(Locale.US,"%+.1f dB",cursorDb)+(cursorTarget.length()>0?"   Target "+cursorTarget:"");text.setTextSize(dp(11));float bw=Math.min(getWidth()-dp(12),text.measureText(val)+dp(16)),bh=dp(32),bx=Math.min(getWidth()-bw-dp(4),cursorX+dp(5)),by=Math.max(r.top+dp(2),Math.min(r.bottom-bh-dp(2),cursorY-bh-dp(5)));p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(228,20,24,30));c.drawRect(bx,by,bx+bw,by+bh,p);text.setColor(Color.WHITE);c.drawText(val,bx+dp(8),by+dp(21),text);}

    private void setCursor(int i,float x,float y){GraphConfig g=cfg[i];float left=dp(44),right=getWidth()-dp(8);double xx=Math.max(left,Math.min(right,x)),t=(xx-left)/(right-left),f=g.fMin*Math.pow(g.fMax/g.fMin,t);double[] h=showLive&&(displayMode!=MODE_SPL||splReady)?liveHz[i]:null,d=h==null?null:liveDb[i];if((h==null||d==null)&&preview!=null&&(displayMode!=MODE_SPL||preview.splCalibrated)){double[][] sm=processed(preview.hz,preview.db,g,preview.splOffset);h=sm[0];d=sm[1];}if(h==null||d==null){for(int q=traces.size()-1;q>=0;q--){MeasurementTrace mt=traces.get(q);if(!mt.visible||(displayMode==MODE_SPL&&!mt.splCalibrated))continue;double[][] sm=processed(mt.hz,mt.db,g,mt.splOffset);h=sm[0];d=sm[1];break;}}if(h==null||d==null||h.length==0)return;int best=0;double dist=Double.MAX_VALUE;for(int k=0;k<h.length;k++){double q=Math.abs(Math.log(h[k]/f));if(q<dist){dist=q;best=k;}}cursorPanel=i;cursorX=mapF(h[best],g.fMin,g.fMax,left,right);cursorY=y;cursorHz=h[best];cursorDb=d[best];invalidate();}

    private float mapF(double f,double mn,double mx,float left,float right){double t=Math.log(f/mn)/Math.log(mx/mn);return left+(float)t*(right-left);}private float mapDb(double v,double mn,double mx,float top,float bottom){double t=(v-mn)/(mx-mn);return bottom-(float)t*(bottom-top);}

    static String formatHz(double f){if(f>=1000){double k=f/1000.0;return k>=10?String.format(Locale.US,"%.0fk",k):String.format(Locale.US,"%.2fk",k);}return f>=100?String.format(Locale.US,"%.0f",f):String.format(Locale.US,"%.1f",f);}private static String avgName(double t){if(t<.2)return "FAST";if(t<.9)return "MED";if(t<2.5)return "SLOW";return "V.SLOW";}

    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent e){scaleDetector.onTouchEvent(e);gestureDetector.onTouchEvent(e);if(!scaleDetector.isInProgress()&&e.getPointerCount()==1&&e.getAction()==MotionEvent.ACTION_MOVE){int i=panelAt(e.getY());if(i>=0){selectedPanel=i;setCursor(i,e.getX(),e.getY());}}return true;}

}
