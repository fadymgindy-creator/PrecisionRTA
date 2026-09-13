package app.precisionrta.v2;

import android.content.Context;
import android.content.SharedPreferences;

final class GraphConfig {
    double fMin, fMax, yMin, yMax;
    int fraction;
    double avgTau;
    int gridDb;
    boolean minorGrid, labels;

    GraphConfig(double fMin,double fMax,double yMin,double yMax,int fraction,double avgTau,int gridDb){
        this.fMin=fMin;this.fMax=fMax;this.yMin=yMin;this.yMax=yMax;this.fraction=fraction;this.avgTau=avgTau;this.gridDb=gridDb;
        this.minorGrid=true;this.labels=true;
    }
    GraphConfig copy(){GraphConfig g=new GraphConfig(fMin,fMax,yMin,yMax,fraction,avgTau,gridDb);g.minorGrid=minorGrid;g.labels=labels;return g;}
    static GraphConfig factory(int i){
        if(i==0)return new GraphConfig(20,20000,-30,30,12,0.5,5);
        if(i==1)return new GraphConfig(20,250,-30,30,12,0.5,5);
        return new GraphConfig(200,20000,-30,30,12,0.5,5);
    }
    static GraphConfig loadCurrent(Context c,int i){return load(c,i,"current_");}
    static GraphConfig loadPreset(Context c,int i){return load(c,i,"preset_");}
    private static GraphConfig load(Context c,int i,String kind){
        SharedPreferences s=c.getSharedPreferences("rta_v2",Context.MODE_PRIVATE);GraphConfig d=factory(i);String p="g"+i+"_"+kind;
        d.fMin=getD(s,p+"fmin",d.fMin);d.fMax=getD(s,p+"fmax",d.fMax);d.yMin=getD(s,p+"ymin",d.yMin);d.yMax=getD(s,p+"ymax",d.yMax);
        d.fraction=s.getInt(p+"frac",d.fraction);d.avgTau=getD(s,p+"tau",d.avgTau);d.gridDb=s.getInt(p+"grid",d.gridDb);d.minorGrid=s.getBoolean(p+"minor",true);d.labels=s.getBoolean(p+"labels",true);
        try{d.sanitize();return d;}catch(IllegalArgumentException e){return factory(i);}
    }
    void saveCurrent(Context c,int i){save(c,i,"current_");}
    void savePreset(Context c,int i){save(c,i,"preset_");}
    private void save(Context c,int i,String kind){sanitize();SharedPreferences.Editor e=c.getSharedPreferences("rta_v2",Context.MODE_PRIVATE).edit();String p="g"+i+"_"+kind;
        putD(e,p+"fmin",fMin);putD(e,p+"fmax",fMax);putD(e,p+"ymin",yMin);putD(e,p+"ymax",yMax);e.putInt(p+"frac",fraction);putD(e,p+"tau",avgTau);e.putInt(p+"grid",gridDb).putBoolean(p+"minor",minorGrid).putBoolean(p+"labels",labels).apply();}
    void sanitize(){
        if(!SpectrumMath.isFinite(fMin)||!SpectrumMath.isFinite(fMax)||!SpectrumMath.isFinite(yMin)||!SpectrumMath.isFinite(yMax)||!SpectrumMath.isFinite(avgTau))throw new IllegalArgumentException("Graph ranges must be finite");
        fMin=Math.max(10,Math.min(19000,fMin));fMax=Math.max(fMin*1.05,Math.min(24000,fMax));
        yMin=Math.max(-120,Math.min(139,yMin));yMax=Math.max(yMin+1,Math.min(140,yMax));
        if(fraction!=3&&fraction!=6&&fraction!=12&&fraction!=24&&fraction!=48)fraction=12;
        if(gridDb!=1&&gridDb!=2&&gridDb!=3&&gridDb!=5&&gridDb!=10)gridDb=5;
        avgTau=Math.max(0.05,Math.min(10,avgTau));
    }
    private static void putD(SharedPreferences.Editor e,String k,double v){e.putLong(k,Double.doubleToRawLongBits(v));}
    private static double getD(SharedPreferences s,String k,double d){return s.contains(k)?Double.longBitsToDouble(s.getLong(k,0)):d;}
}
