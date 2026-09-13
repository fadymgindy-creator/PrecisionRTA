package app.precisionrta.v2;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
final class CalibrationProfile {
 final String name;final double[] hz,addDb;
 String inputName="",orientation="",deviceModel="",referenceCalibration="",caseNotes="";int sampleRate;long createdMs=System.currentTimeMillis();
 CalibrationProfile(String n,double[] h,double[] d){SpectrumMath.validate(h,d);for(double v:d)if(Math.abs(v)>80)throw new IllegalArgumentException("Calibration exceeds +/-80 dB");name=n;hz=h.clone();addDb=d.clone();}
 double correctionAt(double f){if(f<=hz[0])return addDb[0];int last=hz.length-1;if(f>=hz[last])return addDb[last];int lo=0,hi=last;while(hi-lo>1){int m=(lo+hi)>>>1;if(hz[m]<=f)lo=m;else hi=m;}double q=Math.log(f/hz[lo])/Math.log(hz[hi]/hz[lo]);return addDb[lo]+q*(addDb[hi]-addDb[lo]);}
 String fingerprint(){StringBuilder s=new StringBuilder(name);for(int i=0;i<hz.length;i++)s.append('|').append(Double.toHexString(hz[i])).append(':').append(Double.toHexString(addDb[i]));return AtomicStore.key(s.toString()).substring(Math.min(60,s.toString().replaceAll("[^A-Za-z0-9._-]+","_").length())+1);}
 static CalibrationProfile parse(InputStream in,String name,double sign)throws IOException{
  if(sign!=1&&sign!=-1)throw new IOException("Choose a calibration sign convention");
  BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));List<double[]> rows=new ArrayList<>();String line;int lines=0;
  while((line=br.readLine())!=null){if(++lines>25000||line.length()>16384)throw new IOException("Calibration file is too large");line=line.trim();if(line.isEmpty()||line.startsWith("*")||line.startsWith(";")||line.startsWith("#"))continue;String[] p=line.split("[\\s,;]+",6);if(p.length<2)continue;
   try{double f=Double.parseDouble(p[0]),db=Double.parseDouble(p[1]);if(!SpectrumMath.isFinite(f)||!SpectrumMath.isFinite(db)||f<=0)throw new IOException("Invalid calibration row");rows.add(new double[]{f,sign*db});}catch(NumberFormatException ignored){}
  }
  if(rows.size()<2)throw new IOException("Calibration requires two or more numeric frequency/dB rows");Collections.sort(rows,(a,b)->Double.compare(a[0],b[0]));double[] h=new double[rows.size()],d=new double[h.length];for(int i=0;i<h.length;i++){h[i]=rows.get(i)[0];d[i]=rows.get(i)[1];}
  try{return new CalibrationProfile(name,h,d);}catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
 }
 JSONObject toJson()throws Exception{return new JSONObject().put("name",name).put("hz",MeasurementTrace.array(hz)).put("db",MeasurementTrace.array(addDb)).put("input",inputName).put("orientation",orientation).put("model",deviceModel).put("refcal",referenceCalibration).put("case_notes",caseNotes).put("sr",sampleRate).put("created",createdMs);}
 static CalibrationProfile fromJson(JSONObject o)throws Exception{
  CalibrationProfile p=new CalibrationProfile(o.getString("name"),MeasurementTrace.values(o.getJSONArray("hz")),MeasurementTrace.values(o.getJSONArray("db")));
  p.inputName=o.optString("input","");p.orientation=o.optString("orientation","");p.deviceModel=o.optString("model","");p.referenceCalibration=o.optString("refcal","");p.caseNotes=o.optString("case_notes","");p.sampleRate=o.optInt("sr",0);p.createdMs=o.optLong("created",0);return p;
 }
 void save(Context c){try{CalibrationStore.save(c,this);CalibrationStore.setActive(c,name);}catch(Exception e){throw new IllegalStateException("Calibration save failed: "+e.getMessage(),e);}}
 static CalibrationProfile load(Context c){try{String n=CalibrationStore.activeName(c);return n==null?null:CalibrationStore.load(c,n);}catch(Exception e){return null;}}
}
