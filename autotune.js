// Toon Jack Autotune engine
// Pitch correction is built from SoundTouchJS AudioWorklet pitch shifting plus
// a lightweight autocorrelation pitch tracker. The original recording is never overwritten.
const PACKAGE = "https://esm.sh/@soundtouchjs/audio-worklet@2.1.1";
const PROCESSOR = "https://cdn.jsdelivr.net/npm/@soundtouchjs/audio-worklet@2.1.1/.dist/soundtouch-processor.js";
let enginePromise;
async function getEngine(){
  if(!enginePromise) enginePromise=import(PACKAGE).then(m=>m.SoundTouchNode);
  return enginePromise;
}
const SCALES={
  chromatic:[0,1,2,3,4,5,6,7,8,9,10,11],
  major:[0,2,4,5,7,9,11],
  minor:[0,2,3,5,7,8,10],
  pentatonic:[0,2,4,7,9]
};
const KEYS=["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
function nearestShift(freq,settings){
  if(!Number.isFinite(freq)||freq<65||freq>1000)return null;
  const midi=69+12*Math.log2(freq/440);
  const root=KEYS.indexOf(settings.key||"C");
  const scale=SCALES[settings.scale]||SCALES.chromatic;
  let best=0,dist=Infinity;
  for(let n=Math.round(midi)-12;n<=Math.round(midi)+12;n++){
    if(scale.includes(((n-root)%12+12)%12)&&Math.abs(n-midi)<dist){best=n;dist=Math.abs(n-midi)}
  }
  const strength=Math.max(0,Math.min(100,Number(settings.strength??75)))/100;
  return (best-midi)*strength;
}
function detectPitch(buf,sampleRate){
  let rms=0;for(let i=0;i<buf.length;i++)rms+=buf[i]*buf[i];rms=Math.sqrt(rms/buf.length);
  if(rms<0.008)return null;
  const minLag=Math.floor(sampleRate/1000),maxLag=Math.min(Math.floor(sampleRate/65),buf.length-2);
  let bestLag=-1,best=-1;
  for(let lag=minLag;lag<=maxLag;lag++){
    let sum=0;for(let i=0;i<buf.length-lag;i++)sum+=buf[i]*buf[i+lag];
    const score=sum/(buf.length-lag);
    if(score>best){best=score;bestLag=lag}
  }
  if(bestLag<0)return null;
  return sampleRate/bestLag;
}
async function makeGraph(inputStream,settings,monitor=false){
  const SoundTouchNode=await getEngine();
  const Ctx=window.AudioContext||window.webkitAudioContext;
  const ctx=new Ctx();
  await ctx.resume();
  await ctx.audioWorklet.addModule(PROCESSOR);
  const source=ctx.createMediaStreamSource(inputStream);
  const analyser=ctx.createAnalyser();analyser.fftSize=4096;analyser.smoothingTimeConstant=0.15;
  const node=new SoundTouchNode({context:ctx});
  const dest=ctx.createMediaStreamDestination();
  source.connect(analyser);analyser.connect(node);node.connect(dest);
  if(monitor)node.connect(ctx.destination);
  const data=new Float32Array(analyser.fftSize);
  let raf=0,last=0;
  const track=()=>{
    analyser.getFloatTimeDomainData(data);
    const freq=detectPitch(data,ctx.sampleRate);
    if(freq){const shift=nearestShift(freq,settings);if(shift!==null)node.pitchSemitones.setTargetAtTime(shift,ctx.currentTime,settings.mode==="hard"?0.012:0.055)}
    raf=requestAnimationFrame(track);
  };
  track();
  return {stream:dest.stream,stop:async()=>{cancelAnimationFrame(raf);inputStream.getTracks().forEach(t=>t.stop());try{await ctx.close()}catch{}}};
}
window.toonAutotune={
  async live(inputStream,settings,monitor){return makeGraph(inputStream,settings,monitor)},
  async process(blob,settings,onProgress){
    const Ctx=window.AudioContext||window.webkitAudioContext,ctx=new Ctx();
    const SoundTouchNode=await getEngine();
    await ctx.resume();
    await ctx.audioWorklet.addModule(PROCESSOR);
    const raw=await blob.arrayBuffer(),buffer=await ctx.decodeAudioData(raw);
    const source=ctx.createBufferSource();source.buffer=buffer;
    const analyser=ctx.createAnalyser();analyser.fftSize=4096;
    const node=new SoundTouchNode({context:ctx});const dest=ctx.createMediaStreamDestination();
    source.connect(analyser);analyser.connect(node);node.connect(dest);
    const data=new Float32Array(analyser.fftSize);let raf=0;
    const track=()=>{analyser.getFloatTimeDomainData(data);const f=detectPitch(data,ctx.sampleRate);if(f){const s=nearestShift(f,settings);if(s!==null)node.pitchSemitones.setTargetAtTime(s,ctx.currentTime,settings.mode==="hard"?0.012:0.055)}raf=requestAnimationFrame(track)};
    track();
    const mime=["audio/webm;codecs=opus","audio/webm","audio/mp4"].find(t=>MediaRecorder.isTypeSupported(t));
    const rec=new MediaRecorder(dest.stream,mime?{mimeType:mime}:undefined),parts=[];
    const done=new Promise((resolve,reject)=>{rec.ondataavailable=e=>{if(e.data?.size)parts.push(e.data)};rec.onerror=()=>reject(rec.error||new Error("Audio processing failed"));rec.onstop=()=>resolve(new Blob(parts,{type:rec.mimeType||mime||"audio/webm"}))});
    rec.start();source.start();
    await new Promise(resolve=>source.onended=resolve);
    await new Promise(resolve=>setTimeout(resolve,180));rec.stop();
    const result=await done;cancelAnimationFrame(raf);dest.stream.getTracks().forEach(t=>t.stop());await ctx.close();return result;
  }
};
