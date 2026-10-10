"""Exploratory: does padding, gain, repetition or an explicit language change SenseVoice's emotion tag on the
short CREMA-D clips? Prints the tag counts per true emotion for each variant (60 clips x 5 variants x 2 languages).

    ~/.venvs/megathon-signals/bin/python -I sv_variants.py MODEL_DIR CREMAD_DIR

Not part of the main result; used to check that EMO_UNKNOWN on short clips is not an artefact of our audio handling.
"""
import sys, collections, numpy as np, soundfile as sf, sherpa_onnx
from pathlib import Path
M=Path(sys.argv[1]); D=Path(sys.argv[2])
def mk(lang):
    return sherpa_onnx.OfflineRecognizer.from_sense_voice(model=str(M/"model.int8.onnx"),tokens=str(M/"tokens.txt"),num_threads=2,use_itn=True,language=lang)
recs={"auto":mk(""),"en":mk("en")}
files=sorted(D.glob("*.wav"))
print(sf.info(str(files[0])))
def variants(x):
    yield "orig",x
    yield "pad1s",np.concatenate([np.zeros(16000,np.float32),x,np.zeros(16000,np.float32)])
    yield "peaknorm",x/ (np.abs(x).max()+1e-9)*0.9
    yield "repeat3",np.concatenate([x,x,x])
    yield "x4gain",np.clip(x*4,-1,1)
res=collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
for p in files:
    x,sr=sf.read(str(p),dtype="float32"); assert sr==16000
    lab=p.stem.split("_")[2]
    for lang,rec in recs.items():
        for vn,v in variants(x):
            s=rec.create_stream(); s.accept_waveform(16000,v); rec.decode_stream(s)
            res[(lang,vn)][lab][s.result.emotion]+=1
for k,v in res.items():
    print(k)
    for lab in ["ANG","NEU","SAD","FEA","HAP","DIS"]:
        print("  ",lab,dict(v[lab]))
