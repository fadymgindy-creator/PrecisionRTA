"""Independent NumPy reference; checked-in PCM/CSV fixtures need no Python to run JUnit."""
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parents[1]/'test-resources'
root.mkdir(exist_ok=True)
rng=np.random.default_rng(20260911)
for sr,n in [(44100,262144),(48000,16384)]:
 t=np.arange(n)/sr
 x=.18*np.sin(2*np.pi*73.4*t+.73)+.23*np.sin(2*np.pi*1000.37*t)+.07*np.sin(2*np.pi*15000.1*t+.19)+rng.normal(0,.01,n)
 pcm=np.rint(x*32768).astype('<i2');pcm.tofile(root/f'oracle-{sr}-{n}.pcm')
 x=pcm.astype(float)/32768;x-=x.mean()
 w=np.sin(np.pi*np.arange(n)/(n-1))**2
 z=np.fft.rfft(x*w);power=abs(z)**2/(n*np.dot(w,w));power[1:-1]*=2
 assert abs(power.sum()-np.dot(x*w,x*w)/np.dot(w,w))<1e-12
 centers=1000*2.0**(np.arange(-270,208)/48) # independently bound to 20..20k below
 centers=centers[(centers>=20)&(centers<=20000)]
 df=sr/n;k=np.arange(n//2+1);a=np.maximum(0,(k-.5)*df);b=np.minimum(sr/2,(k+.5)*df)
 rows=[]
 for f in centers:
  lower=f*2**(-1/96);upper=f*2**(1/96)
  overlap=np.maximum(0,np.minimum(b,upper)-np.maximum(a,lower))
  integrated=np.dot(power,overlap/(b-a))
  rows.append((f,10*np.log10(max(1e-30,integrated))))
 np.savetxt(root/f'oracle-{sr}-{n}.csv',rows,delimiter=',',fmt='%.16g')
 print(f'{sr} Hz / {n}: {len(rows)} independently calculated bands')
