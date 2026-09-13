package app.precisionrta.v2;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Random;
/** New broadband checks: theoretical bandwidth/PSD laws, independent of band implementation. */
public class NoiseEnsembleTest {
 @Test public void whiteNoiseTracksLinearBandwidth(){
  int n=65536,sr=48000,count=32;Random random=new Random(73925);double[] mean=null,h=null;
  for(int frame=0;frame<count;frame++){short[] pcm=new short[n];for(int i=0;i<n;i++)pcm[i]=(short)Math.round(32768*.05*random.nextGaussian());SpectrumProcessor.Result r=SpectrumProcessor.process(pcm,sr,null);double[][] bands=SpectrumMath.smooth(r.hz,r.db,3,100,10000);h=bands[0];if(mean==null)mean=new double[h.length];for(int i=0;i<mean.length;i++)mean[i]+=SpectrumMath.dbToPower(bands[1][i])/count;}
  double worst=0;for(int i=0;i<h.length;i++){double width=h[i]*(Math.pow(2,1.0/6)-Math.pow(2,-1.0/6));double expected=.05*.05*2*width/sr;double error=10*Math.log10(mean[i]/expected);worst=Math.max(worst,Math.abs(error));assertEquals("White noise band "+h[i],0,error,.35);}System.out.println("White noise: worst band error="+worst+" dB / 32 frames");
 }
 @Test public void pinkNoiseHasEqualOctaveEnergy(){
  int n=65536,sr=48000,count=32;Random random=new Random(8831);double[] mean=null,h=null;
  // Inverse DFT of prescribed 1/f PSD generates the test signal. RealFft has a separate direct-DFT proof.
  for(int frame=0;frame<count;frame++){double[] re=new double[n],im=new double[n];for(int k=1;k<n/2;k++){double phase=random.nextDouble()*2*Math.PI,amp=1/Math.sqrt(k);re[k]=re[n-k]=amp*Math.cos(phase);im[k]=amp*Math.sin(phase);im[n-k]=-im[k];}RealFft.fft(re,im);short[] pcm=new short[n];for(int i=0;i<n;i++)pcm[i]=(short)Math.round(32768*.018*re[i]);SpectrumProcessor.Result r=SpectrumProcessor.process(pcm,sr,null);assertEquals(0,r.clippedSamples);double[][] bands=SpectrumMath.smooth(r.hz,r.db,3,100,10000);h=bands[0];if(mean==null)mean=new double[h.length];for(int i=0;i<mean.length;i++)mean[i]+=SpectrumMath.dbToPower(bands[1][i])/count;}
  double average=0;for(double p:mean)average+=p/mean.length;double worst=0;for(int i=0;i<h.length;i++){double error=10*Math.log10(mean[i]/average);worst=Math.max(worst,Math.abs(error));assertEquals("Pink noise band "+h[i],0,error,.6);}System.out.println("Pink noise: worst band deviation="+worst+" dB / 32 frames");
 }
}
