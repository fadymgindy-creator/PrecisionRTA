package app.precisionrta.v2;
import java.util.ArrayList;
import java.util.List;
final class SpectrumMath {
 private SpectrumMath(){}
 static boolean isFinite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
 static double dbToPower(double db){return Math.pow(10,db/10);}
 static double powerToDb(double p){return 10*Math.log10(Math.max(1e-30,p));}
 static void validate(double[] hz,double[] db){
  if(hz==null||db==null||hz.length!=db.length||hz.length<2||hz.length>200000)throw new IllegalArgumentException("Invalid spectrum lengths");
  for(int i=0;i<hz.length;i++)if(!SpectrumMath.isFinite(hz[i])||hz[i]<=0||!SpectrumMath.isFinite(db[i])||(i>0&&hz[i]<=hz[i-1]))throw new IllegalArgumentException("Spectrum requires finite values and increasing positive frequencies");
 }
 static boolean sameGrid(double[] a,double[] b){if(a==null||b==null||a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(Math.abs(a[i]/b[i]-1)>1e-9)return false;return true;}
 // Integrated powers are summed. Partial source-band energy is allocated by logarithmic overlap.
 static double[][] smooth(double[] hz,double[] db,int fraction,double minF,double maxF){
  if(hz==null||db==null||hz.length<2)return new double[][]{new double[0],new double[0]};validate(hz,db);
  double sourceStep=Math.log(hz[1]/hz[0]),targetStep=Math.log(2)/fraction;if(targetStep<sourceStep-1e-8)targetStep=sourceStep;
  List<Double> oh=new ArrayList<>(),od=new ArrayList<>();
  int first=(int)Math.ceil(Math.log(minF/1000)/targetStep-1e-10),last=(int)Math.floor(Math.log(maxF/1000)/targetStep+1e-10);
  double srcLo=Math.log(hz[0])-sourceStep/2,srcHi=Math.log(hz[hz.length-1])+sourceStep/2;
  for(int k=first;k<=last;k++){
   double center=Math.log(1000)+k*targetStep,lo=center-targetStep/2,hi=center+targetStep/2;if(lo<srcLo-1e-8||hi>srcHi+1e-8)continue;
   double sum=0;for(int i=0;i<hz.length;i++){double c=Math.log(hz[i]),overlap=Math.max(0,Math.min(hi,c+sourceStep/2)-Math.max(lo,c-sourceStep/2));if(overlap>0)sum+=dbToPower(db[i])*overlap/sourceStep;}
   oh.add(Math.exp(center));od.add(powerToDb(sum));
  }
  double[] h=new double[oh.size()],d=new double[oh.size()];for(int i=0;i<h.length;i++){h[i]=oh.get(i);d[i]=od.get(i);}return new double[][]{h,d};
 }
 static double normalizeOffset(double[] hz,double[] db,double lo,double hi){double sum=0;int n=0;for(int i=0;i<hz.length;i++)if(hz[i]>=lo&&hz[i]<=hi){sum+=dbToPower(db[i]);n++;}return n==0?0:powerToDb(sum/n);}
 static double broadbandDb(double[] db){double s=0;for(double d:db)s+=dbToPower(d);return powerToDb(s);}
}
