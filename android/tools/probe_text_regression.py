#!/usr/bin/env python3
"""Compile actual prior/current text helpers, run identical hostile fixtures, preserve counterexample."""
import hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/text-regression';OUT.mkdir(parents=True,exist_ok=True)
report=ROOT/'docs/verification/r12b-text-counterexample.json';report.unlink(missing_ok=True)
path='android/format/native/src/main/kotlin/dev/fusion/native/TextFormats.kt';baseline='02d56ae287bf77b98890418861017a53d6acc539'
old=subprocess.check_output(['git','show',f'{baseline}:{path}'],cwd=ROOT.parent)
new=(ROOT.parent/path).read_bytes()
runner=OUT/'TextAttack.kt';runner.write_text('''import dev.fusion.native.TextFormats
import java.util.Base64
fun main() {
 println(TextFormats.isProbablyUtf8(byteArrayOf(0xff.toByte())))
 println(TextFormats.csvRows("\\\"a\\nb\\\",").joinToString(";") { row -> row.joinToString(",") { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } })
}
''')
compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
results={}
for name,source in [('before',old),('after',new)]:
    src=OUT/(name+'.kt');src.write_bytes(source);jar=OUT/(name+'.jar')
    subprocess.run([compiler,'-jvm-target','17',str(src),str(runner),'-include-runtime','-d',str(jar)],check=True)
    p=subprocess.run([java,'-jar',str(jar)],text=True,capture_output=True,check=True)
    results[name]={'sha256':hashlib.sha256(source).hexdigest(),'stdout':p.stdout,'exit_code':p.returncode}
assert results['before']['stdout']=='true\nYQ==;Yiw=\n',results
assert results['after']['stdout']=='false\nYQpi,\n',results
report.write_text(json.dumps({'baseline':baseline,'scope':'Actual JVM prior/current source counterexample; not a native Android A/B test','fixtures':['single FF byte must not be valid UTF8','quoted multiline cell and trailing empty cell must remain one row with two fields'],'results':results,'runner_sha256':hashlib.sha256(runner.read_bytes()).hexdigest(),'script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()},indent=2)+'\n')
print('Actual old source reproduced both defects; repaired source passes identical fixtures.')
