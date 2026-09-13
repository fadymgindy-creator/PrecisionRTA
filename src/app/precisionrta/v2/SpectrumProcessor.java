package app.precisionrta.v2;
import java.util.Arrays;
/** Production PCM -> band-power path shared by analyzer and calibration. */
final class SpectrumProcessor {
 static final int BASE_FRACTION=48;
 static final String UNITS="band-rms-fs2-v1";
 static final class Result {
  double[] hz,db,rawDb; double totalPower,correctedTotalPower,seconds,transientDb;
  int sampleRate,fftSize,clippedSamples; String invalidReason="",qualityWarning="";
 }
 static int fftSize(int f){if(f<=3)return 16384;if(f<=6)return 32768;if(f<=12)return 65536;if(f<=24)return 131072;return 262144;}
 static double resolutionLimit(int sr,int n,int fraction){return 4.0*sr/n/(Math.pow(2,.5/fraction)-Math.pow(2,-.5/fraction));}
 static double[] binPowers(short[] pcm){
  int n=pcm.length;if(n<8||(n&(n-1))!=0)throw new IllegalArgumentException("FFT length must be a power of two >= 8");
  double mean=0;for(short s:pcm)mean+=s/32768.0;mean/=n;
  double[] re=new double[n],im=new double[n];double sumW2=0;
  for(int i=0;i<n;i++){double w=.5-.5*Math.cos(2*Math.PI*i/(n-1.0));sumW2+=w*w;re[i]=(pcm[i]/32768.0-mean)*w;}
  RealFft.fft(re,im);double[] power=new double[n/2+1];
  for(int k=0;k<power.length;k++)power[k]=(k==0||k==n/2?1:2)*(re[k]*re[k]+im[k]*im[k])/(n*sumW2);
  return power;
 }
 static Result process(short[] pcm,int sr,CalibrationProfile correction){
  if(sr<8000||sr>192000)throw new IllegalArgumentException("Invalid stream sample rate");
  Result r=new Result();r.sampleRate=sr;r.fftSize=pcm.length;r.seconds=pcm.length/(double)sr;
  int zeros=0,maxZeros=0;for(short s:pcm){if(Math.abs((int)s)>=32760)r.clippedSamples++;if(s==0){zeros++;maxZeros=Math.max(maxZeros,zeros);}else zeros=0;}
  if(r.clippedSamples>0)r.invalidReason="Near digital clipping — lower the input level";
  else if(maxZeros>=sr/4)r.invalidReason="Digital silence/dropout — check microphone access";
  int block=Math.max(1,sr/10),count=(pcm.length+block-1)/block;double[] shortPower=new double[count];
  for(int j=0;j<count;j++){int end=Math.min(pcm.length,(j+1)*block);double p=0;for(int i=j*block;i<end;i++){double x=pcm[i]/32768.0;p+=x*x;}shortPower[j]=p/(end-j*block);}
  Arrays.sort(shortPower);r.transientDb=SpectrumMath.powerToDb(shortPower[count-1])-SpectrumMath.powerToDb(shortPower[count/2]);
  double[] raw=binPowers(pcm),corrected=raw.clone();double df=sr/(double)pcm.length;
  for(int k=0;k<raw.length;k++){r.totalPower+=raw[k];if(correction!=null&&k>0)corrected[k]*=Math.pow(10,correction.correctionAt(k*df)/10);r.correctedTotalPower+=corrected[k];}
  if(r.totalPower<1e-7)r.qualityWarning="Very low digital level: PCM16 quantization limits accuracy";
  double step=Math.pow(2,1.0/BASE_FRACTION),edge=Math.pow(2,.5/BASE_FRACTION);
  int first=(int)Math.ceil(Math.log(20.0/1000)/Math.log(step)),last=(int)Math.floor(Math.log(Math.min(20000.0,sr/2.0/edge)/1000)/Math.log(step));
  r.hz=new double[last-first+1];r.db=new double[r.hz.length];r.rawDb=new double[r.hz.length];
  for(int j=0;j<r.hz.length;j++){double f=1000*Math.pow(step,first+j);r.hz[j]=f;r.rawDb[j]=SpectrumMath.powerToDb(integrate(raw,df,f/edge,f*edge));r.db[j]=SpectrumMath.powerToDb(integrate(corrected,df,f/edge,f*edge));}
  return r;
 }
 // Bin cells have constant density. Fractional edges conserve their energy.
 static double integrate(double[] power,double df,double lo,double hi){
  double sum=0;int first=Math.max(0,(int)Math.floor(lo/df-.5)),last=Math.min(power.length-1,(int)Math.ceil(hi/df+.5));
  for(int k=first;k<=last;k++){double a=Math.max(0,(k-.5)*df),b=Math.min((power.length-1)*df,(k+.5)*df),overlap=Math.max(0,Math.min(hi,b)-Math.max(lo,a));if(b>a)sum+=power[k]*overlap/(b-a);}return sum;
 }
}
