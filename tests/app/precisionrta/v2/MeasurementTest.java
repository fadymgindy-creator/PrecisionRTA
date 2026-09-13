package app.precisionrta.v2;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MeasurementTest {
 private short[] tone(int n,int sr,double frequency,double amplitude,double phase){short[] s=new short[n];for(int i=0;i<n;i++)s[i]=(short)Math.round(32768*amplitude*Math.sin(2*Math.PI*frequency*i/sr+phase));return s;}
 private double sum(double[] p){double v=0;for(double x:p)v+=x;return v;}
 @Test public void fftMatchesDirectDft(){
  Random rng=new Random(1928);for(int n:new int[]{8,32,128}){double[] re=new double[n],im=new double[n],a=new double[n],b=new double[n];for(int i=0;i<n;i++){re[i]=a[i]=rng.nextDouble()-.5;im[i]=b[i]=rng.nextDouble()-.5;}RealFft.fft(re,im);
   for(int k=0;k<n;k++){double r=0,q=0;for(int i=0;i<n;i++){double angle=2*Math.PI*i*k/n;r+=a[i]*Math.cos(angle)+b[i]*Math.sin(angle);q+=b[i]*Math.cos(angle)-a[i]*Math.sin(angle);}assertEquals(r,re[k],1e-9);assertEquals(q,im[k],1e-9);}}
 }
 @Test public void fftRejectsBadSizes(){assertThrows(IllegalArgumentException.class,()->RealFft.fft(new double[7],new double[7]));assertThrows(IllegalArgumentException.class,()->RealFft.fft(new double[8],new double[4]));}
 @Test public void parsevalMatchesTimeDomain(){
  Random rng=new Random(43);for(int type=0;type<5;type++){short[] s=new short[16384];for(int i=0;i<s.length;i++)s[i]=type==0?0:type==1?1234:type==2?(short)(i==773?20000:0):type==3?(short)(i%2==0?12000:-12000):(short)rng.nextInt(65536);
   double mean=0;for(short v:s)mean+=v/32768.0;mean/=s.length;double weighted=0,ws=0;
   for(int i=0;i<s.length;i++){double w=Math.pow(Math.sin(Math.PI*i/(s.length-1)),2),x=s[i]/32768.0-mean;weighted+=x*x*w*w;ws+=w*w;}
   double expected=weighted/ws;assertEquals(expected,sum(SpectrumProcessor.binPowers(s)),Math.max(1e-12,expected*1e-8));}
 }
 @Test public void knownTonesAcrossRatesSizesLevels(){
  for(int sr:new int[]{44100,48000})for(int n:new int[]{16384,32768,65536,131072,262144})for(double amplitude:new double[]{.5,.01,.0001})for(double frequency:new double[]{73.4,1000.37,15000.1}){
   short[] pcm=tone(n,sr,frequency,amplitude,.71);SpectrumProcessor.Result r=SpectrumProcessor.process(pcm,sr,null);
   if(amplitude>=.001)assertEquals(10*Math.log10(amplitude*amplitude/2),SpectrumMath.powerToDb(r.totalPower),.05);
   else {double mean=0;for(short v:pcm)mean+=v/32768.0;mean/=n;double energy=0,weight=0;for(int i=0;i<n;i++){double w=Math.pow(Math.sin(Math.PI*i/(n-1)),4),x=pcm[i]/32768.0-mean;energy+=w*x*x;weight+=w;}assertEquals(energy/weight,r.totalPower,Math.max(1e-14,energy/weight*1e-8));}
   for(double d:r.db)assertTrue(Double.isFinite(d));assertTrue(r.invalidReason,r.invalidReason.isEmpty());
  }
 }
 @Test public void veryLowToneDocumentsQuantizationLimit(){SpectrumProcessor.Result r=SpectrumProcessor.process(tone(16384,48000,15000.1,.0001,.71),48000,null);assertEquals(-83.6136103687644,SpectrumMath.powerToDb(r.totalPower),1e-8);assertTrue(Math.abs(SpectrumMath.powerToDb(r.totalPower)-10*Math.log10(.0001*.0001/2))>.5);assertFalse(r.qualityWarning.isEmpty());}
 @Test public void amplitudeDoublingIsSixDb(){for(int sr:new int[]{44100,48000}){double a=SpectrumProcessor.process(tone(262144,sr,1000,.1,.2),sr,null).totalPower,b=SpectrumProcessor.process(tone(262144,sr,1000,.2,.2),sr,null).totalPower;assertEquals(6.020599913,10*Math.log10(b/a),.02);}}
 @Test public void integrationConservesCellsAndToneAtBoundary(){
  double[] p={.1,.2,.3,.4,.5};double all=SpectrumProcessor.integrate(p,1,0,4),partition=0;for(double a=0;a<4;a+=.125)partition+=SpectrumProcessor.integrate(p,1,a,a+.125);assertEquals(sum(p),all,1e-12);assertEquals(all,partition,1e-12);
  SpectrumProcessor.Result r=SpectrumProcessor.process(tone(262144,48000,1000*Math.pow(2,.5/48),.5,0),48000,null);double bandPower=0;for(int i=0;i<r.hz.length;i++)if(r.hz[i]>900&&r.hz[i]<1100)bandPower+=SpectrumMath.dbToPower(r.db[i]);assertEquals(-9.03089987,SpectrumMath.powerToDb(bandPower),.05);
 }
 @Test public void calibrationPowerGainAndRawPreservation(){
  short[] pcm=tone(65536,48000,1000,.1,.2);CalibrationProfile cp=new CalibrationProfile("+6",new double[]{10,24000},new double[]{6,6});SpectrumProcessor.Result raw=SpectrumProcessor.process(pcm,48000,null),corrected=SpectrumProcessor.process(pcm,48000,cp);
  assertArrayEquals(raw.rawDb,corrected.rawDb,0);for(int i=0;i<raw.db.length;i++)if(raw.db[i]>-250)assertEquals(6,corrected.db[i]-raw.db[i],1e-8);
 }
 @Test public void smoothingEnergyAndNonDestructiveNormalization(){
  SpectrumProcessor.Result r=SpectrumProcessor.process(tone(16384,48000,1000,.1,0),48000,null);double[] db=new double[r.hz.length];Arrays.fill(db,-40);double[] saved=db.clone(),hz=r.hz.clone();
  for(int f:new int[]{3,6,12,24,48}){double[][] sm=SpectrumMath.smooth(hz,db,f,50,16000);for(double v:sm[1])assertEquals(-40+10*Math.log10(48.0/f),v,1e-8);SpectrumMath.normalizeOffset(hz,db,500,2000);assertArrayEquals(saved,db,0);assertArrayEquals(r.hz,hz,0);}
 }
 @Test public void averageUsesPowerAndExactDuration(){
  MeasurementAccumulator a=new MeasurementAccumulator(7.5);double[] h={100,200},loud={0,0},quiet={-20,-20};a.add(h,loud,loud,1,"");a.add(h,quiet,quiet,3,"");MeasurementTrace t=a.trace("a");assertEquals(.2575,SpectrumMath.dbToPower(t.db[0]),1e-10);assertEquals(4,t.acceptedSeconds,0);a.add(h,loud,loud,2,"clipped");assertEquals(2,a.rejectedSeconds(),0);assertEquals(2,a.accepted());assertThrows(IllegalArgumentException.class,()->a.add(new double[]{101,201},loud,loud,1,""));
  MeasurementAccumulator equal=new MeasurementAccumulator(7.5);equal.add(h,loud,loud,1,"");equal.add(h,quiet,quiet,1,"");assertEquals(.505,SpectrumMath.dbToPower(equal.trace("eq").db[0]),1e-10);
 }
 @Test public void transientClippingSilenceAndSustainedLevel(){
  MeasurementAccumulator a=new MeasurementAccumulator(7.5);SpectrumProcessor.Result steady=SpectrumProcessor.process(tone(262144,48000,1000,.05,0),48000,null);assertTrue(a.add(steady));assertTrue(a.add(SpectrumProcessor.process(tone(262144,48000,1000,.3,0),48000,null)));
  short[] impulse=tone(262144,48000,1000,.01,0);for(int i=50000;i<54000;i++)impulse[i]*=20;assertFalse(a.add(SpectrumProcessor.process(impulse,48000,null)));
  short[] clipped=tone(262144,48000,1000,.1,0);clipped[888]=32767;assertFalse(a.add(SpectrumProcessor.process(clipped,48000,null)));assertFalse(a.add(SpectrumProcessor.process(new short[262144],48000,null)));assertEquals(2,a.accepted());assertEquals(3,a.rejected());assertEquals(3*262144.0/48000,a.rejectedSeconds(),1e-12);
 }
 @Test public void calibrationParsingSignInterpolationValidation()throws Exception{
  byte[] file="# error\n100 2 123\n10000 -4\n".getBytes(StandardCharsets.UTF_8);CalibrationProfile cp=CalibrationProfile.parse(new ByteArrayInputStream(file),"cal",-1);assertEquals(1,cp.correctionAt(1000),1e-10);assertEquals(-2,cp.correctionAt(10),0);assertEquals(4,cp.correctionAt(20000),0);
  assertThrows(IOException.class,()->CalibrationProfile.parse(new ByteArrayInputStream("100 1\n100 2\n".getBytes()),"duplicate",1));assertThrows(IOException.class,()->CalibrationProfile.parse(new ByteArrayInputStream("100 NaN\n1000 2\n".getBytes()),"bad",1));
  cp.inputName="USB";cp.orientation="landscape";cp.caseNotes="case on";CalibrationProfile loaded=CalibrationProfile.fromJson(new JSONObject(cp.toJson().toString()));assertArrayEquals(cp.addDb,loaded.addDb,0);assertArrayEquals(cp.hz,loaded.hz,0);assertEquals(cp.fingerprint(),loaded.fingerprint());assertEquals(cp.caseNotes,loaded.caseNotes);
 }
 @Test public void autoCalReferenceDifferenceAndVerification(){
  SpectrumProcessor.Result r=SpectrumProcessor.process(tone(262144,48000,1000,.1,0),48000,null);double[] ref=new double[r.hz.length],phone=new double[ref.length];for(int i=0;i<ref.length;i++){ref[i]=-30;phone[i]=-34;}
  CalibrationProfile cp=AutoCalibrator.makeProfile("shape",r.hz,ref,r.hz,phone);for(double x:cp.addDb)assertEquals(0,x,1e-9);assertEquals(0,AutoCalibrator.verificationRms(r.hz,ref,r.hz,phone,null,50,10000),1e-9);double[] altered=r.hz.clone();altered[0]*=.99;assertThrows(IllegalArgumentException.class,()->AutoCalibrator.makeProfile("bad",r.hz,ref,altered,phone));
 }
 @Test public void traceSessionAndSpatialRoundTrip()throws Exception{
  double[] h={100,200},d={-10,-20};MeasurementTrace a=new MeasurementTrace("one",h,d);a.sampleRate=48000;a.calibrationName="test";a.calibrationId="same";a.inputName="usb";a.orientation="portrait";a.rawDb=new double[]{-12,-22};a.splCalibrated=true;a.splOffset=90;
  MeasurementTrace b=MeasurementTrace.fromJson(new JSONObject(a.toJson().toString()));b.db=new double[]{-20,-30};assertArrayEquals(a.rawDb,b.rawDb,0);MeasurementTrace avg=MeasurementTrace.spatialAverage(Arrays.asList(a,b));assertEquals(.055,SpectrumMath.dbToPower(avg.db[0]),1e-10);assertArrayEquals(d,a.db,0);b.inputName="different";assertThrows(IllegalArgumentException.class,()->MeasurementTrace.spatialAverage(Arrays.asList(a,b)));
  File f=File.createTempFile("rta-session", ".json");f.delete();GraphConfig[] g={GraphConfig.factory(0),GraphConfig.factory(1),GraphConfig.factory(2)};SessionStore.saveFile(f,"project","session",Arrays.asList(a),g,500,2000,0);SessionStore.Loaded loaded=SessionStore.loadFile(f);assertArrayEquals(a.db,loaded.traces.get(0).db,0);assertEquals(90,loaded.traces.get(0).splOffset,0);assertEquals(20000,loaded.graphs[0].fMax,0);
  SessionStore.saveFile(f,"project","second",Arrays.asList(a),g,500,2000,0);try(FileOutputStream out=new FileOutputStream(f)){out.write("{broken".getBytes());}assertEquals("session",SessionStore.loadFile(f).session);AtomicStore.delete(f);
 }
 @Test public void invalidStoredNumbersAndGraphSettingsFail(){
  assertThrows(IllegalArgumentException.class,()->new MeasurementTrace("bad",new double[]{100,100},new double[]{1,2}));assertThrows(IllegalArgumentException.class,()->new MeasurementTrace("bad",new double[]{100,200},new double[]{1,Double.NaN}));GraphConfig g=GraphConfig.factory(0);g.fMax=Double.NaN;assertThrows(IllegalArgumentException.class,g::sanitize);assertFalse(AtomicStore.key("a/b").equals(AtomicStore.key("a_b")));
 }
 @Test public void numpyOracleFixtures()throws Exception{
  for(String name:new String[]{"44100-262144","48000-16384"}){
   byte[] bytes;try(InputStream in=getClass().getResourceAsStream("/oracle-"+name+".pcm")){assertNotNull(in);bytes=in.readAllBytes();}ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);short[] pcm=new short[bytes.length/2];for(int i=0;i<pcm.length;i++)pcm[i]=b.getShort();int sr=Integer.parseInt(name.split("-")[0]);SpectrumProcessor.Result actual=SpectrumProcessor.process(pcm,sr,null);
   try(BufferedReader in=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/oracle-"+name+".csv"),StandardCharsets.UTF_8))){String line;int i=0;while((line=in.readLine())!=null){String[] v=line.split(",");assertEquals(Double.parseDouble(v[0]),actual.hz[i],1e-7);double expected=Double.parseDouble(v[1]);if(expected>-140)assertEquals("band "+i,expected,actual.db[i],1e-6);i++;}assertEquals(actual.hz.length,i);}
  }
 }
}
