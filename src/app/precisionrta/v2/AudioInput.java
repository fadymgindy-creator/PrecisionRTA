package app.precisionrta.v2;
import android.content.Context;
import android.media.*;
import android.media.audiofx.*;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
/** Owns one recorder. Routing must be observed after recording begins. */
final class AudioInput implements AutoCloseable {
 final AudioRecord record;final int sampleRate;final String sourceName;
 private final AudioDeviceInfo requested;private int routeId=-1;private long delivered;
 String description="";String processingWarning="Hardware processing unverified";
 private final List<AudioEffect> effects=new ArrayList<>();
 private AudioInput(AudioRecord r,AudioDeviceInfo d,String source){record=r;requested=d;sampleRate=r.getSampleRate();sourceName=source;}
 static AudioInput open(Context c,AudioDeviceInfo d) throws Exception {
  if(c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
   throw new SecurityException("Microphone permission is not granted");
  AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
  boolean raw=Build.VERSION.SDK_INT>=24&&am!=null&&"true".equalsIgnoreCase(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
  Exception last=null;
  for(int source:raw?new int[]{MediaRecorder.AudioSource.UNPROCESSED,MediaRecorder.AudioSource.VOICE_RECOGNITION}:new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION}){
   for(int sr:new int[]{48000,44100}){
    AudioRecord ar=null;
    try{
     int min=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)continue;
     ar=new AudioRecord(source,sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*4,sr*2));
     if(ar.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Input initialization failed");
     if(d!=null&&!ar.setPreferredDevice(d))throw new IllegalStateException("Android refused selected input");
     AudioInput input=new AudioInput(ar,d,source==MediaRecorder.AudioSource.UNPROCESSED?"UNPROCESSED":"VOICE_RECOGNITION");
     input.disableEffects();ar.startRecording();if(ar.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("Microphone did not start");
     return input;
    }catch(Exception e){last=e;if(ar!=null)try{ar.release();}catch(Exception ignored){}}
   }
  }
  throw new IllegalStateException("Cannot open input: "+(last==null?"unsupported rate":last.getMessage()));
 }
 private void disableEffects(){
  try{if(AutomaticGainControl.isAvailable()){AutomaticGainControl e=AutomaticGainControl.create(record.getAudioSessionId());if(e!=null){effects.add(e);e.setEnabled(false);}}}catch(Exception ignored){}
  try{if(NoiseSuppressor.isAvailable()){NoiseSuppressor e=NoiseSuppressor.create(record.getAudioSessionId());if(e!=null){effects.add(e);e.setEnabled(false);}}}catch(Exception ignored){}
  try{if(AcousticEchoCanceler.isAvailable()){AcousticEchoCanceler e=AcousticEchoCanceler.create(record.getAudioSessionId());if(e!=null){effects.add(e);e.setEnabled(false);}}}catch(Exception ignored){}
  for(AudioEffect e:effects)try{if(e.getEnabled())processingWarning="Android effect remains enabled; hardware processing unverified";}catch(Exception ignored){}
 }
 int read(short[] data) throws Exception {
  int n=record.read(data,0,data.length,AudioRecord.READ_BLOCKING);
  if(n<0)throw new IllegalStateException("AudioRecord read failed ("+n+")");
  if(n==0)throw new IllegalStateException("Audio input stopped delivering samples");
  delivered+=n;
  AudioDeviceInfo routed=record.getRoutedDevice();
  if(routed==null)throw new IllegalStateException("Android did not report actual microphone routing");
  if(requested!=null&&routed.getId()!=requested.getId())throw new IllegalStateException("Actual microphone differs from selected input");
  if(routeId!=-1&&routeId!=routed.getId())throw new IllegalStateException("Input route changed; start a new measurement");
  routeId=routed.getId();
  if(record.getSampleRate()!=sampleRate)throw new IllegalStateException("Stream sample rate changed");
  String deviceRate="";
  if(Build.VERSION.SDK_INT>=29){
   AudioRecordingConfiguration config=record.getActiveRecordingConfiguration();
   if(config!=null){if(config.isClientSilenced())throw new IllegalStateException("Android silenced microphone capture");
    deviceRate="; device stream "+config.getFormat().getSampleRate()+" Hz";
   }
  }
  if(Build.VERSION.SDK_INT>=24){
   AudioTimestamp ts=new AudioTimestamp();
   if(record.getTimestamp(ts,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS&&ts.framePosition-delivered>record.getBufferSizeInFrames()+sampleRate/4)
    throw new IllegalStateException("Capture backlog exceeded buffer; possible lost audio");
  }
  description=routed.getProductName()+" ["+AnalyzerEngine.typeName(routed.getType())+"] id="+routeId+"; mono PCM16; "+sampleRate+" Hz; "+sourceName+deviceRate;
  return n;
 }
 void stop(){try{record.stop();}catch(Exception ignored){}}
 public void close(){stop();for(AudioEffect e:effects)try{e.release();}catch(Exception ignored){}try{record.release();}catch(Exception ignored){}}
}
