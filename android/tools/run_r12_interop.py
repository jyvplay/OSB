#!/usr/bin/env python3
"""Independent Python frame oracle against compiled production Kotlin Notes draft codec.
Actual compilation/processes, not a substitute Android runtime. No external libraries.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import random
import struct
import subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'testing/build/r12-interop'
KOTLIN='''
import dev.fusion.notes.*
import java.io.*
import java.util.Base64
object R12Interop {
 @JvmStatic fun main(args:Array<String>) {
  val b64=Base64.getEncoder();val decoder=Base64.getDecoder()
  generateSequence(::readLine).forEach { line ->
   val fields=line.split('\\t')
   when(fields[0]) {
    "W" -> {
     val text=String(decoder.decode(fields[2]),Charsets.UTF_8)
     val out=ByteArrayOutputStream();NoteDraftCodec.write(NoteDraft(fields[1],text),out)
     println(b64.encodeToString(out.toByteArray()))
    }
    "R" -> try {
     val draft=NoteDraftCodec.read(ByteArrayInputStream(decoder.decode(fields[1])))
     println("OK:"+b64.encodeToString(draft.text.toByteArray(Charsets.UTF_8)))
    } catch(e:Exception) { println("REFUSED") }
    else -> error("Invalid oracle operation")
   }
  }
 }
}
'''

def frame(body):return struct.pack('>II',0x4e445231,len(body))+body+hashlib.sha256(body).digest()
def field(text):
    b=text.encode('utf-8');return struct.pack('>I',len(b))+b

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    report=ROOT/'docs/verification/r12-interop.json';report.unlink(missing_ok=True)
    runner=OUT/'R12Interop.kt';runner.write_text(KOTLIN)
    sources=sorted((ROOT/'feature/notes/src/main/kotlin').rglob('*.kt'))
    jar=OUT/'oracle.jar'
    subprocess.run([os.environ.get('KOTLINC','kotlinc'),'-jvm-target','17',*map(str,sources),str(runner),'-include-runtime','-d',str(jar)],check=True)
    randomizer=random.Random(120012);alphabet=['a','Z','0','界','🙂','\x00','\n','\r','é','[',']',' ']
    texts=[''.join(randomizer.choice(alphabet) for _ in range(randomizer.randrange(301))) for _ in range(200)]
    expected=[frame(field('Draft_'+str(i))+field(text)+field('')+b'\0') for i,text in enumerate(texts)]
    b64=lambda b:base64.b64encode(b).decode('ascii')
    requests=['W\tDraft_'+str(i)+'\t'+b64(text.encode('utf-8')) for i,text in enumerate(texts)]
    requests+=['R\t'+b64(b) for b in expected]
    base=expected[0]
    attacks=[base[:-1],base+b'\0',bytes([base[0]^1])+base[1:],base[:4]+struct.pack('>I',2**31-1)+base[8:],
             base[:-1]+bytes([base[-1]^1]),frame(field('x')+field('x')+field('')+b'\2'),
             frame(struct.pack('>I',1)+b'\xff'+field('x')+field('')+b'\0'),
             frame(field('x')+field('x')+field('orphan')+b'\0'),
             frame(field('x'*65)+field('x')+field('')+b'\0'),
             frame(field('x')+struct.pack('>I',0xffffffff)+field('')+b'\0')]
    requests+=['R\t'+b64(b) for b in attacks]
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    result=subprocess.run([java,'-cp',str(jar),'R12Interop'],input='\n'.join(requests)+'\n',text=True,capture_output=True,check=True,timeout=30)
    actual=result.stdout.splitlines()
    wanted=[b64(b) for b in expected]+['OK:'+b64(t.encode('utf-8')) for t in texts]+['REFUSED']*len(attacks)
    if actual!=wanted: raise RuntimeError('Python/Kotlin frame oracle disagreed: '+str(next((i for i,pair in enumerate(zip(actual,wanted)) if pair[0]!=pair[1]),len(actual))))
    data={'status':'PASS','checks':len(wanted),'write_exact_frame':200,'read_python_frame':200,'malformed_attacks':len(attacks),'seed':120012,
      'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
      'driver_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
      'scope':'Python independently constructs exact frames/checksums and malformed resealed frames; real compiled Kotlin codec writes/reads them. Not Android persistence or physical process-death verification.'}
    report.write_text(json.dumps(data,indent=2)+'\n');print(json.dumps(data,indent=2))

if __name__=='__main__':main()
