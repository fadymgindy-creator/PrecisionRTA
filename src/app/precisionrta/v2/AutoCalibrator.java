package app.precisionrta.v2;
import android.content.Context;
import android.media.*;
import java.util.concurrent.atomic.AtomicLong;
final class AutoCalibrator {
 interface Callback{void done(double[] hz,double[] db,String error);}
 private static final AtomicLong generation=new AtomicLong();private static volatile Thread worker;private static volatile AudioInput current;
 static volatile String lastInput="";static volatile int lastSampleRate;
 static AudioDeviceInfo findExternalInput(Context c){AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);if(am==null)return null;for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS)){int t=d.getType();if(t==AudioDeviceInfo.TYPE_USB_DEVICE||t==AudioDeviceInfo.TYPE_USB_HEADSET||t==AudioDeviceInfo.TYPE_WIRED_HEADSET)return d;}return null;}
 static AudioDeviceInfo findBuiltIn(Context c){AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);if(am==null)return null;for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)return d;return null;}
 static void cancel(){generation.incrementAndGet();AudioInput a=current;if(a!=null)a.stop();Thread t=worker;if(t!=null)t.interrupt();}
 static synchronized void capture(Context c,AudioDeviceInfo dev,CalibrationProfile correction,Callback callback){
  if(worker!=null&&worker.isAlive()){callback.done(null,null,"A calibration capture is still stopping");return;}
  if(dev==null){callback.done(null,null,"Selected microphone is unavailable; default routing cannot be trusted for calibration");return;}
  long token=generation.incrementAndGet();Context app=c.getApplicationContext();
  worker=new Thread(()->{
   AudioInput input=null;
   try{
    input=AudioInput.open(app,dev);current=input;final int n=262144;
    short[] frame=new short[n],buffer=new short[4096];int pos=0,frames=0;MeasurementAccumulator acc=new MeasurementAccumulator(7.5);
    while(generation.get()==token&&frames<6){
     int read=input.read(buffer);
     for(int i=0;i<read;i++){
      frame[pos++]=buffer[i];
      if(pos==n){SpectrumProcessor.Result result=SpectrumProcessor.process(frame,input.sampleRate,correction);if(!acc.add(result))throw new IllegalStateException("Calibration contained clipping, silence or a transient. Keep playback and position steady and retry.");frames++;pos=0;}
     }
    }
    if(generation.get()!=token)return;MeasurementTrace trace=acc.trace("Calibration capture");if(trace==null)throw new IllegalStateException("No complete calibration capture");
    lastInput=input.description;lastSampleRate=input.sampleRate;callback.done(trace.hz,trace.db,null);
   }catch(Exception e){if(generation.get()==token)callback.done(null,null,e.getMessage());}
   finally{if(input!=null)input.close();if(current==input)current=null;worker=null;}
  },"RTA-Calibration");worker.start();
 }
 static CalibrationProfile makeProfile(String name,double[] rh,double[] rd,double[] ph,double[] pd){
  SpectrumMath.validate(rh,rd);SpectrumMath.validate(ph,pd);if(!SpectrumMath.sameGrid(rh,ph))throw new IllegalArgumentException("Reference and phone frequency grids differ");
  double[][] ref=SpectrumMath.smooth(rh,rd,12,20,20000),phone=SpectrumMath.smooth(ph,pd,12,20,20000);
  double[] h=ref[0],corr=new double[h.length];double offset=0;int count=0;
  for(int i=0;i<h.length;i++){if(ref[1][i]<-110||phone[1][i]<-110)throw new IllegalArgumentException("Calibration signal is too weak in part of the spectrum");corr[i]=ref[1][i]-phone[1][i];if(h[i]>=500&&h[i]<=2000){offset+=corr[i];count++;}}
  if(count==0)throw new IllegalArgumentException("Calibration needs 500–2000 Hz coverage");offset/=count;
  for(int i=0;i<corr.length;i++){corr[i]-=offset;if(Math.abs(corr[i])>24)throw new IllegalArgumentException("Correction exceeds 24 dB. Check routing, noise, and microphone placement; no curve was saved.");}
  return new CalibrationProfile(name,h,corr);
 }
 static double verificationRms(double[] rh,double[] rd,double[] ph,double[] pd,CalibrationProfile cp,double lo,double hi){
  if(!SpectrumMath.sameGrid(rh,ph))throw new IllegalArgumentException("Verification grids differ");
  double[][] ref=SpectrumMath.smooth(rh,rd,12,lo,hi),phone=SpectrumMath.smooth(ph,pd,12,lo,hi);
  int n=ref[0].length;if(n==0)throw new IllegalArgumentException("No frequencies in verification range");
  double[] error=new double[n];double mean=0;
  for(int i=0;i<n;i++){error[i]=phone[1][i]-ref[1][i]+(cp==null?0:cp.correctionAt(ref[0][i]));mean+=error[i];}mean/=n;double sum=0;for(double e:error)sum+=(e-mean)*(e-mean);return Math.sqrt(sum/n);
 }
}
