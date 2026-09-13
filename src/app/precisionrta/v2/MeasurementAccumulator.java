package app.precisionrta.v2;
final class MeasurementAccumulator {
 private double[] hz,sumPower,sumRaw;private int accepted,rejected;private double acceptedSec,rejectedSec;private final double threshold;
 MeasurementAccumulator(double thresholdDb){threshold=thresholdDb;}
 boolean add(SpectrumProcessor.Result r){String reason=r.invalidReason;if(reason.isEmpty()&&r.transientDb>threshold)reason="Short transient within FFT window";return add(r.hz,r.db,r.rawDb,r.seconds,reason);}
 boolean add(double[] h,double[] db,double[] raw,double seconds,String rejection){
  SpectrumMath.validate(h,db);SpectrumMath.validate(h,raw);
  if(!SpectrumMath.isFinite(seconds)||seconds<=0)throw new IllegalArgumentException("Invalid sample duration");
  if(hz!=null&&!SpectrumMath.sameGrid(hz,h))throw new IllegalArgumentException("Acquisition grid changed during measurement");
  if(rejection!=null&&!rejection.isEmpty()){rejected++;rejectedSec+=seconds;return false;}
  if(hz==null){hz=h.clone();sumPower=new double[h.length];sumRaw=new double[h.length];}
  for(int i=0;i<h.length;i++){sumPower[i]+=SpectrumMath.dbToPower(db[i])*seconds;sumRaw[i]+=SpectrumMath.dbToPower(raw[i])*seconds;}
  accepted++;acceptedSec+=seconds;return true;
 }
 MeasurementTrace trace(String name){
  if(accepted==0)return null;double[] d=new double[hz.length],raw=new double[d.length];
  for(int i=0;i<d.length;i++){d[i]=SpectrumMath.powerToDb(sumPower[i]/acceptedSec);raw[i]=SpectrumMath.powerToDb(sumRaw[i]/acceptedSec);}
  MeasurementTrace t=new MeasurementTrace(name,hz,d);t.rawDb=raw;t.acceptedFrames=accepted;t.rejectedFrames=rejected;t.acceptedSeconds=acceptedSec;t.rejectedSeconds=rejectedSec;return t;
 }
 int accepted(){return accepted;}int rejected(){return rejected;}double acceptedSeconds(){return acceptedSec;}double rejectedSeconds(){return rejectedSec;}
}
