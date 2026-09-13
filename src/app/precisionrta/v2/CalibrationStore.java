package app.precisionrta.v2;
import android.content.Context;
import java.io.*;
import java.util.*;
final class CalibrationStore {
 private CalibrationStore(){}
 static File dir(Context c){return new File(c.getFilesDir(),"calibrations");}
 static void save(Context c,CalibrationProfile p)throws Exception{AtomicStore.write(new File(dir(c),AtomicStore.key(p.name)+".json"),p.toJson().toString());}
 static CalibrationProfile load(Context c,String name)throws Exception{
  File f=new File(dir(c),AtomicStore.key(name)+".json");
  if(!f.exists()&&!new File(f+".bak").exists())return null;return CalibrationProfile.fromJson(AtomicStore.readJson(f));
 }
 static String[] names(Context c){File[] files=dir(c).listFiles();if(files==null)return new String[0];List<String> names=new ArrayList<>();for(File f:files)if(f.getName().endsWith(".json"))try{names.add(CalibrationProfile.fromJson(AtomicStore.readJson(f)).name);}catch(Exception ignored){}Collections.sort(names);return names.toArray(new String[0]);}
 static void setActive(Context c,String n){c.getSharedPreferences("rta_v2",Context.MODE_PRIVATE).edit().putString("active_cal",n).apply();}
 static String activeName(Context c){return c.getSharedPreferences("rta_v2",Context.MODE_PRIVATE).getString("active_cal",null);}
 static void saveReference(Context c,CalibrationProfile p)throws Exception{AtomicStore.write(new File(c.getFilesDir(),"reference-calibration.json"),p.toJson().toString());}
 static CalibrationProfile loadReference(Context c){try{return CalibrationProfile.fromJson(AtomicStore.readJson(new File(c.getFilesDir(),"reference-calibration.json")));}catch(Exception e){return null;}}
}
