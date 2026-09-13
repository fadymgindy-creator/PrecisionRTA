package app.precisionrta.v2;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.io.File;
import java.util.Arrays;
/** Real Android JSON/filesystem and large-FFT checks. No synthetic results are saved as user measurements. */
public class DeviceChecks extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Override public void onStart(){
  Bundle results=new Bundle();File fixture=new File(getTargetContext().getCacheDir(),"rta-instrumentation-session.json");
  try{
   short[] pcm=new short[262144];for(int i=0;i<pcm.length;i++)pcm[i]=(short)Math.round(16384*Math.sin(2*Math.PI*1000*i/48000));
   long begin=System.nanoTime();SpectrumProcessor.Result s=SpectrumProcessor.process(pcm,48000,null);double ms=(System.nanoTime()-begin)/1e6;
   require(Math.abs(SpectrumMath.powerToDb(s.totalPower)+9.03089987)<.05,"Tone normalization on Android");
   MeasurementAccumulator acc=new MeasurementAccumulator(7.5);require(acc.add(s),"Steady tone rejected");MeasurementTrace trace=acc.trace("synthetic instrumentation fixture");
   trace.sampleRate=48000;trace.inputName="SYNTHETIC TEST ONLY";trace.orientation="test";
   GraphConfig[] graphs={GraphConfig.factory(0),GraphConfig.factory(1),GraphConfig.factory(2)};
   SessionStore.saveFile(fixture,"instrumentation","temporary",Arrays.asList(trace),graphs,500,2000,0);
   SessionStore.Loaded loaded=SessionStore.loadFile(fixture);require(Arrays.equals(trace.db,loaded.traces.get(0).db),"Android corrected array round trip");require(Arrays.equals(trace.rawDb,loaded.traces.get(0).rawDb),"Android raw array round trip");
   CalibrationProfile cp=new CalibrationProfile("test",new double[]{100,10000},new double[]{-2,4});CalibrationProfile restored=CalibrationProfile.fromJson(new org.json.JSONObject(cp.toJson().toString()));require(cp.fingerprint().equals(restored.fingerprint()),"Android calibration JSON round trip");
   SessionStore.Loaded captured=SessionStore.loadTemp(getTargetContext());int recovered=0;if(captured!=null)for(MeasurementTrace t:captured.traces){require(t.hz.length>=470,"Fine data lost");require(t.rawDb.length==t.db.length,"Raw data missing");require(t.fftSize==262144,"Formal FFT size wrong");require(t.acceptedFrames>0,"No accepted data");require(Math.abs(t.acceptedSeconds-t.acceptedFrames*262144.0/t.sampleRate)<1e-9,"Duration does not match sample counts");recovered++;}
   results.putString("stream","PASS: Android tone DSP; JSON raw/corrected/calibration round trips; recovered ambient traces="+recovered+"; 262144-point production processing="+String.format(java.util.Locale.US,"%.1f",ms)+" ms. Synthetic checks do not establish acoustic accuracy.\n");
   finish(Activity.RESULT_OK,results);
  }catch(Throwable t){results.putString("stream","FAIL: "+t.toString()+"\n");finish(Activity.RESULT_CANCELED,results);}
  finally{AtomicStore.delete(fixture);}
 }
}
