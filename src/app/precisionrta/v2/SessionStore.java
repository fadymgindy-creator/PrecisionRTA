package app.precisionrta.v2;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
final class SessionStore {
 private SessionStore(){}
 static File dir(Context c){return new File(c.getFilesDir(),"sessions");}
 static void save(Context c,String project,String session,List<MeasurementTrace> traces,GraphConfig[] graphs,double normLo,double normHi,int mode,boolean temp)throws Exception{
  File file=temp?new File(c.getFilesDir(),"current-session.json"):new File(dir(c),AtomicStore.key(project+"__"+session)+".json");saveFile(file,project,session,traces,graphs,normLo,normHi,mode);
 }
 static void saveFile(File file,String project,String session,List<MeasurementTrace> traces,GraphConfig[] graphs,double normLo,double normHi,int mode)throws Exception{
  JSONObject o=new JSONObject().put("version",3).put("project",project).put("session",session).put("saved",System.currentTimeMillis()).put("norm_lo",normLo).put("norm_hi",normHi).put("display_mode",mode);
  JSONArray a=new JSONArray();for(MeasurementTrace t:traces)a.put(t.toJson());o.put("traces",a);JSONArray ga=new JSONArray();
  if(graphs!=null)for(GraphConfig g:graphs)ga.put(new JSONObject().put("fmin",g.fMin).put("fmax",g.fMax).put("ymin",g.yMin).put("ymax",g.yMax).put("frac",g.fraction).put("tau",g.avgTau).put("grid",g.gridDb).put("minor",g.minorGrid).put("labels",g.labels));o.put("graphs",ga);AtomicStore.write(file,o.toString());
 }
 static Loaded loadFile(File f)throws Exception{
  JSONObject o=AtomicStore.readJson(f);if(o.getInt("version")!=3)throw new IOException("This session uses old/unknown measurement units");
  Loaded l=new Loaded();l.project=o.optString("project","Project");l.session=o.optString("session","Session");l.normLo=o.optDouble("norm_lo",500);l.normHi=o.optDouble("norm_hi",2000);l.displayMode=o.optInt("display_mode",0);
  if(!SpectrumMath.isFinite(l.normLo)||!SpectrumMath.isFinite(l.normHi)||l.normLo<20||l.normHi>20000||l.normHi<=l.normLo||l.displayMode<0||l.displayMode>2)throw new IOException("Invalid session display settings");
  JSONArray a=o.getJSONArray("traces");if(a.length()>500)throw new IOException("Too many traces");for(int i=0;i<a.length();i++)l.traces.add(MeasurementTrace.fromJson(a.getJSONObject(i)));
  JSONArray ga=o.optJSONArray("graphs");if(ga!=null&&ga.length()==3){l.graphs=new GraphConfig[3];for(int i=0;i<3;i++){JSONObject x=ga.getJSONObject(i);GraphConfig g=GraphConfig.factory(i);g.fMin=x.getDouble("fmin");g.fMax=x.getDouble("fmax");g.yMin=x.getDouble("ymin");g.yMax=x.getDouble("ymax");g.fraction=x.getInt("frac");g.avgTau=x.getDouble("tau");g.gridDb=x.getInt("grid");g.minorGrid=x.optBoolean("minor",true);g.labels=x.optBoolean("labels",true);g.sanitize();l.graphs[i]=g;}}return l;
 }
 static Loaded loadTemp(Context c)throws Exception{File f=new File(c.getFilesDir(),"current-session.json");return f.exists()||new File(f+".bak").exists()?loadFile(f):null;}
 static void clearTemp(Context c){AtomicStore.delete(new File(c.getFilesDir(),"current-session.json"));}
 static File[] list(Context c){File[] f=dir(c).listFiles((d,n)->n.endsWith(".json"));return f==null?new File[0]:f;}
 static byte[] readAll(File f)throws Exception{return AtomicStore.readAll(f);}
 static final class Loaded{String project,session;List<MeasurementTrace> traces=new ArrayList<>();GraphConfig[] graphs;double normLo=500,normHi=2000;int displayMode;}
}
