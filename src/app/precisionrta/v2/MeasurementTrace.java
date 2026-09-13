package app.precisionrta.v2;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
final class MeasurementTrace {
 String name;long timeMs;double[] hz,db,rawDb;boolean visible=true,spatial=false;
 int acceptedFrames,rejectedFrames,sampleRate,fftSize=262144;double acceptedSeconds,rejectedSeconds;
 String inputName="",calibrationName="None",calibrationId="",orientation="",processing="",caseNotes="";
 String units=SpectrumProcessor.UNITS;boolean splCalibrated;double splOffset;JSONObject calibrationSnapshot;
 MeasurementTrace(String n,double[] h,double[] d){SpectrumMath.validate(h,d);name=n;hz=h.clone();db=d.clone();rawDb=d.clone();timeMs=System.currentTimeMillis();}
 JSONObject toJson()throws Exception{
  JSONObject o=new JSONObject();o.put("name",name).put("time",timeMs).put("visible",visible).put("spatial",spatial).put("accepted",acceptedFrames).put("rejected",rejectedFrames).put("accepted_sec",acceptedSeconds).put("rejected_sec",rejectedSeconds).put("input",inputName).put("sr",sampleRate).put("fft",fftSize).put("cal",calibrationName).put("cal_id",calibrationId).put("orientation",orientation).put("processing",processing).put("case_notes",caseNotes).put("units",units).put("spl_calibrated",splCalibrated).put("spl_offset",splOffset);
  o.put("hz",array(hz)).put("db",array(db)).put("raw_db",array(rawDb));if(calibrationSnapshot!=null)o.put("calibration",calibrationSnapshot);return o;
 }
 static JSONArray array(double[] d)throws Exception{JSONArray a=new JSONArray();for(double v:d)a.put(v);return a;}
 static double[] values(JSONArray a)throws Exception{if(a.length()>200000)throw new IllegalArgumentException("Spectrum too large");double[] d=new double[a.length()];for(int i=0;i<d.length;i++)d[i]=a.getDouble(i);return d;}
 static MeasurementTrace fromJson(JSONObject o)throws Exception{
  if(!SpectrumProcessor.UNITS.equals(o.optString("units")))throw new IllegalArgumentException("Legacy/unknown level units cannot be interpreted reliably");
  MeasurementTrace t=new MeasurementTrace(o.getString("name"),values(o.getJSONArray("hz")),values(o.getJSONArray("db")));
  t.rawDb=values(o.getJSONArray("raw_db"));SpectrumMath.validate(t.hz,t.rawDb);t.timeMs=o.optLong("time",0);t.visible=o.optBoolean("visible",true);t.spatial=o.optBoolean("spatial",false);
  t.acceptedFrames=o.optInt("accepted",0);t.rejectedFrames=o.optInt("rejected",0);t.acceptedSeconds=o.optDouble("accepted_sec",0);t.rejectedSeconds=o.optDouble("rejected_sec",0);
  if(!SpectrumMath.isFinite(t.acceptedSeconds)||!SpectrumMath.isFinite(t.rejectedSeconds)||t.acceptedSeconds<0||t.rejectedSeconds<0)throw new IllegalArgumentException("Invalid captured duration");
  t.inputName=o.optString("input","");t.sampleRate=o.optInt("sr",0);t.fftSize=o.optInt("fft",262144);t.calibrationName=o.optString("cal","None");t.calibrationId=o.optString("cal_id","");t.orientation=o.optString("orientation","");t.processing=o.optString("processing","");t.caseNotes=o.optString("case_notes","");t.calibrationSnapshot=o.optJSONObject("calibration");t.splCalibrated=o.optBoolean("spl_calibrated",false);t.splOffset=o.optDouble("spl_offset",0);
  if(!SpectrumMath.isFinite(t.splOffset))throw new IllegalArgumentException("Invalid SPL reference");return t;
 }
 static MeasurementTrace spatialAverage(List<MeasurementTrace> traces){
  if(traces.size()<2)throw new IllegalArgumentException("Select at least two measurements");
  MeasurementTrace first=traces.get(0);double[] power=new double[first.hz.length],raw=new double[power.length];
  for(MeasurementTrace t:traces){
   if(t.spatial||!SpectrumMath.sameGrid(first.hz,t.hz)||!first.units.equals(t.units)||first.sampleRate!=t.sampleRate||first.fftSize!=t.fftSize||!first.inputName.equals(t.inputName)||!first.calibrationId.equals(t.calibrationId)||!first.orientation.equals(t.orientation)||!first.caseNotes.equals(t.caseNotes))
    throw new IllegalArgumentException("Selected traces have different input, calibration, orientation, case, resolution or units. Measure them with the same setup.");
   for(int i=0;i<power.length;i++){power[i]+=SpectrumMath.dbToPower(t.db[i])/traces.size();raw[i]+=SpectrumMath.dbToPower(t.rawDb[i])/traces.size();}
  }
  for(int i=0;i<power.length;i++){power[i]=SpectrumMath.powerToDb(power[i]);raw[i]=SpectrumMath.powerToDb(raw[i]);}
  MeasurementTrace out=new MeasurementTrace("Spatial Average ("+traces.size()+")",first.hz,power);out.rawDb=raw;out.spatial=true;out.inputName=first.inputName;out.sampleRate=first.sampleRate;out.fftSize=first.fftSize;out.calibrationName=first.calibrationName;out.calibrationId=first.calibrationId;out.calibrationSnapshot=first.calibrationSnapshot;out.orientation=first.orientation;out.caseNotes=first.caseNotes;out.processing=first.processing;out.acceptedFrames=traces.size();
  out.splCalibrated=first.splCalibrated;out.splOffset=first.splOffset;
  for(MeasurementTrace t:traces)if(t.splCalibrated!=first.splCalibrated||Math.abs(t.splOffset-first.splOffset)>1e-9)out.splCalibrated=false;
  return out;
 }
}
