#!/usr/bin/env python3
"""Compile real pure modules separately, then run contracts against their public APIs.
Needs Java17+ and Kotlin2.2.21. Does not download tools or replace Android APIs.
"""
import hashlib
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
compiler = os.environ.get('KOTLINC', 'kotlinc')
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
folders = ['core/model', 'core/windowing', 'core/commands', 'core/index', 'core/documents',
           'format/native', 'format/archive', 'format/ooxml', 'cast/airplay', 'cast/castv2']
out = root/'testing/build/contracts'
out.mkdir(parents=True, exist_ok=True)
sources = []
jars = []
subprocess.run([compiler, '-version'], check=True)
for folder in folders + ['feature/calc', 'feature/notes']:
    source_root = root/folder/('src/main/kotlin' if folder in ('feature/calc', 'feature/notes') else 'src/main')
    module_sources = sorted(source_root.rglob('*.kt'))
    module_name = folder.replace('/', '_')
    jar = out/(module_name+'.jar')
    subprocess.run([compiler, '-jvm-target', '17', '-module-name', module_name,
                    *map(str,module_sources), '-d', str(jar)], check=True)
    sources += module_sources
    jars.append(jar)
# Negative baseline and a nested-property attack must fail for the right reason.
negative = root/'testing/src/boundary/negative/PendingCopyBefore.kt'
positive = root/'testing/src/boundary/positive/PendingCopyAfter.kt'
boundary_cp = str(out/'core_documents.jar')
rejected = subprocess.run([compiler, '-jvm-target', '17', '-classpath', boundary_cp,
                           str(negative), '-d', str(out/'rejected-boundary.jar')], text=True, capture_output=True)
if rejected.returncode == 0 or rejected.stderr.lower().count('smart cast') != 2:
    raise RuntimeError('Expected both cross-module nullable-property attacks to be rejected.\n'+rejected.stderr)
subprocess.run([compiler, '-jvm-target', '17', '-classpath', boundary_cp,
                str(positive), '-d', str(out/'accepted-boundary.jar')], check=True)
(out/'boundary-result.txt').write_text('Expected negative compilation exit: '+str(rejected.returncode)+'\n'+rejected.stderr+
    '\nStable-local positive consumer: compilation passed. Neither fixture is an Android UI build.\n'+
    '\n'.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(root)) for p in (negative,positive))+'\n')
sources.append(positive)
contracts = [root/'core/windowing/src/contract/kotlin/dev/fusion/windowing/WindowRestoreChecks.kt',
             root/'feature/notes/src/contract/kotlin/dev/fusion/notes/NotesChecks.kt',
             root/'core/files/src/main/kotlin/dev/fusion/files/FileMutationPolicy.kt',
             root/'testing/src/contract/kotlin/dev/fusion/testing/SearchAndGateChecks.kt',
             root/'core/documents/src/contract/kotlin/dev/fusion/documents/EditorChecks.kt',
             root/'core/documents/src/contract/kotlin/dev/fusion/documents/CopyChecks.kt',
             root/'feature/calc/src/contract/kotlin/dev/fusion/calc/CalculatorChecks.kt',
             root/'feature/beam/src/main/java/dev/fusion/beam/QualityGovernor.kt',
             root/'core/perf/src/main/java/dev/fusion/perf/PerformancePolicy.kt',
             root/'testing/src/contract/kotlin/dev/fusion/testing/PilotCoreChecks.kt']
jar = out/'pilot-contracts.jar'
classpath = os.pathsep.join(map(str,jars))
subprocess.run([compiler, '-jvm-target', '17', '-module-name', 'contracts', '-include-runtime',
                '-classpath', classpath, *map(str,contracts), '-d', str(jar)], check=True)
stdout = 'Two negative module-boundary attacks rejected; stable-local consumer compiled.\nProduction sources compiled as 12 separate JVM libraries (calculator/notes kernels only); consumers compiled against their public APIs. Android sources excluded.\n\n'
for main, args in [('dev.fusion.testing.PilotCoreChecks', []), ('dev.fusion.calc.CalculatorChecks', []),
                   ('dev.fusion.documents.EditorChecks', []),
                   ('dev.fusion.documents.CopyChecks', [str(root/'core/documents/src/contract/resources')]),
                   ('dev.fusion.notes.NotesChecks', []), ('dev.fusion.testing.SearchAndGateChecks', []),
                   ('dev.fusion.windowing.WindowRestoreChecks', [str(root/'core/windowing/src/contract/resources')])]:
    result = subprocess.run([java, '-cp', os.pathsep.join([str(jar),classpath]), main, *args], check=True, text=True, capture_output=True)
    stdout += result.stdout+'\n'
print(stdout, end='')
sources += contracts
fingerprints = '\n'.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root)}' for p in sources)
(out/'result.txt').write_text(stdout+'\nCompiled source SHA-256:\n'+fingerprints+'\n')
print('Evidence saved to testing/build/contracts/result.txt. This is not an Android build.')
