package app.precisionrta.v2;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;
final class AtomicStore {
 private AtomicStore(){}
 static String key(String name){
  try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format(java.util.Locale.US,"%02x",b&255));String clean=name.replaceAll("[^A-Za-z0-9._-]+","_");return clean.substring(0,Math.min(60,clean.length()))+"_"+h.substring(0,16);}catch(Exception e){throw new IllegalStateException(e);}
 }
 static synchronized void write(File file,String text)throws IOException{
  File parent=file.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create storage directory");
  File tmp=new File(file+".tmp"),bak=new File(file+".bak");
  if(bak.exists()&&!file.exists()&&!bak.renameTo(file))throw new IOException("Cannot recover previous save");
  try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
  if(bak.exists()&&!bak.delete())throw new IOException("Cannot rotate storage backup");
  if(file.exists()&&!file.renameTo(bak))throw new IOException("Cannot preserve previous save");
  if(!tmp.renameTo(file)){if(bak.exists())bak.renameTo(file);throw new IOException("Cannot commit saved data");}
  // Keep the previous valid version as recovery against truncation/corruption.
 }
 static synchronized JSONObject readJson(File file)throws Exception{
  Exception error=null;for(File f:new File[]{file,new File(file+".bak")})if(f.exists())try{return new JSONObject(new String(readAll(f),StandardCharsets.UTF_8));}catch(Exception e){error=e;}
  throw error==null?new FileNotFoundException(file.toString()):error;
 }
 static byte[] readAll(File file)throws IOException{
  if(file.length()>32*1024*1024)throw new IOException("Saved file exceeds 32 MiB limit");
  try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
 }
 static void delete(File file){file.delete();new File(file+".bak").delete();new File(file+".tmp").delete();}
}
