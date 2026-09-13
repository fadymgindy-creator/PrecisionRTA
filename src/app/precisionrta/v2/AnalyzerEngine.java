package app.precisionrta.v2;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.os.Process;
/** Sample-counted acquisition. Each recorder and callback belongs to one generation. */
final class AnalyzerEngine {
 interface Listener{void onFrame(Frame frame);void onStatus(String status);}
 static final class Frame {
  final SpectrumProcessor.Result spectrum;final String input,processing;final CalibrationProfile calibration;final long token;final boolean formal;
  Frame(SpectrumProcessor.Result s,String i,String p,CalibrationProfile c,long t,boolean f){spectrum=s;input=i;processing=p;calibration=c;token=t;formal=f;}
 }
 private final Context context;private final Listener listener;private final Object lock=new Object(),ringLock=new Object();
 private final short[] ring=new short[1048576];private long total;private volatile long generation;
 private volatile boolean running,freeze,formal;private volatile int fraction=12;
 private volatile CalibrationProfile calibration;private volatile AudioDeviceInfo preferred;private volatile AudioInput input;
 private Thread audioThread,analysisThread;
 AnalyzerEngine(Context c,Listener l){context=c.getApplicationContext();listener=l;}
 void setBaseFraction(int f){fraction=f;}int getFraction(){return fraction;}
 void setCalibration(CalibrationProfile p){calibration=p;}CalibrationProfile getCalibration(){return calibration;}
 void setPreferredDevice(AudioDeviceInfo d){preferred=d;}AudioDeviceInfo getPreferredDevice(){return preferred;}
 boolean isRunning(){return running;}boolean isFrozen(){return freeze;}void setFreeze(boolean b){freeze=b;}
 boolean isCurrent(Frame f){return running&&f.token==generation;}
 void beginMeasurement(){stop();formal=true;fraction=48;start();}
 void endMeasurement(){stop();formal=false;}
 void restart(){stop();start();}
 private boolean active(long token){return running&&generation==token;}
 void start(){
  synchronized(lock){
   if(running)return;
   if((audioThread!=null&&audioThread.isAlive())||(analysisThread!=null&&analysisThread.isAlive())){listener.onStatus("Audio is still stopping. Tap RESUME in a moment.");return;}
   final long token=++generation;final boolean measuring=formal;final CalibrationProfile cp=calibration;final AudioDeviceInfo dev=preferred;
   running=true;freeze=false;synchronized(ringLock){total=0;}
   audioThread=new Thread(()->audioLoop(token,dev),"RTA-Audio");
   analysisThread=new Thread(()->analysisLoop(token,measuring,cp),"RTA-FFT");
   audioThread.start();analysisThread.start();
  }
 }
 void stop(){
  Thread a,b;AudioInput old;
  synchronized(lock){running=false;generation++;a=audioThread;b=analysisThread;old=input;if(old!=null)old.stop();if(a!=null)a.interrupt();if(b!=null)b.interrupt();}
  synchronized(ringLock){ringLock.notifyAll();}
  join(a);join(b);
 }
 private void join(Thread t){if(t!=null&&t!=Thread.currentThread())try{t.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 private void fail(long token,String message){
  if(!active(token))return;listener.onStatus(message);running=false;AudioInput a=input;if(a!=null)a.stop();synchronized(ringLock){ringLock.notifyAll();}
 }
 private void audioLoop(long token,AudioDeviceInfo dev){
  AudioInput local=null;
  try{
   Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);local=AudioInput.open(context,dev);
   synchronized(lock){if(!active(token))return;input=local;}
   short[] buffer=new short[4096];
   while(active(token)){
    int n=local.read(buffer);
    synchronized(ringLock){if(!active(token))break;for(int i=0;i<n;i++)ring[(int)((total+i)%ring.length)]=buffer[i];total+=n;ringLock.notifyAll();}
   }
  }catch(Exception e){fail(token,"Audio stopped: "+e.getMessage());}
  finally{if(local!=null)local.close();synchronized(lock){if(input==local)input=null;if(audioThread==Thread.currentThread())audioThread=null;}}
 }
 private void analysisLoop(long token,boolean measuring,CalibrationProfile cp){
  long consumed=0,lastEnd=0;int previousN=0;
  try{
   while(active(token)){
    int n=SpectrumProcessor.fftSize(measuring?48:fraction);short[] samples;AudioInput current;
    synchronized(ringLock){
     current=input;
     if(current==null||freeze||total<n||(!measuring&&total-lastEnd<Math.max(4096,n/4))||(measuring&&total-consumed<n)){ringLock.wait(100);continue;}
     long end=measuring?consumed+n:total;
     if(measuring&&total-consumed>ring.length)throw new IllegalStateException("Analysis queue overrun; measurement stopped");
     samples=new short[n];long begin=end-n;
     for(int i=0;i<n;i++)samples[i]=ring[(int)((begin+i)%ring.length)];
     consumed=end;lastEnd=end;previousN=n;
    }
    SpectrumProcessor.Result result=SpectrumProcessor.process(samples,current.sampleRate,cp);
    if(active(token))listener.onFrame(new Frame(result,current.description,current.processingWarning,cp,token,measuring));
   }
  }catch(InterruptedException e){Thread.currentThread().interrupt();}
  catch(Exception e){fail(token,"Analysis stopped: "+e.getMessage());}
  finally{synchronized(lock){if(analysisThread==Thread.currentThread())analysisThread=null;}}
 }
 static String typeName(int t){if(t==AudioDeviceInfo.TYPE_BUILTIN_MIC)return "built-in";if(t==AudioDeviceInfo.TYPE_USB_DEVICE)return "USB audio";if(t==AudioDeviceInfo.TYPE_USB_HEADSET)return "USB headset";if(t==AudioDeviceInfo.TYPE_WIRED_HEADSET)return "wired headset";if(t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO)return "Bluetooth SCO";return "input "+t;}
}
