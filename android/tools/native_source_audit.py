#!/usr/bin/env python3
"""Review ALL recorded requirements and native main-source ownership. Not Kotlin/Gradle type checking."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
FAMILIES={
 'SH':['app','feature/launcher'], 'WM':['app','core/windowing'], 'SR':['feature/find','core/commands','core/index'],
 'FM':['core/files','core/fileops','feature/files','core/documents'], 'NC':['feature/alerts','feature/quickpanel','app'], 'ST':['core/settings','feature/settings','core/capabilities'],
 'AP':['feature/launcher','app'], 'TD':['feature/terminal'], 'PR':['feature/editor','feature/notes','feature/calc','feature/browser','feature/office','feature/ime'],
 'FF':['format/native','format/lok','format/ooxml','format/poi','format/pdf','format/image','format/psd','format/archive','core/documents'],
 'MD':['feature/media','feature/photos','feature/capture','feature/lens'], 'SC':['core/security','feature/vault'],
 'BK':['feature/backup'], 'CT':['feature/continuity','feature/together'], 'CA':['feature/beam','cast/airplay','cast/castv2','cast/dlna','feature/together'],
 'AX':['feature/accessibility','core/designsystem','app'], 'AU':['feature/automation'], 'RG':['core/perf','core/hw','benchmark','baselineprofile'],
 'CM':['app'], 'BL':['app'], 'IN':['app','feature/ime','feature/continuity'], 'LN':['feature/lens','feature/meet'], 'MS':['feature/messages']}

# Cross-cutting obligations cannot be inferred merely from a similarly named feature/module.
FOUNDATIONS=[
 ('Native H1 desktop, not stock desktop replacement','Partial own Compose host; external display is still a noninteractive session mirror.'),
 ('v2.1 below-floor Compat, not hardware hard block','No floor hard block; measured Compat/Standard/Studio policy incomplete.'),
 ('API34 minimum / compile37 / target36→37','min34/compile37/target37 declared via actual37.0 package/AGP9.1.1/Gradle9.3.1; source inventory is not execution evidence; candidate-specific results in R23-RESULTS.md.'),
 ('arm64 and 4K/16K native pages','No linked native/transitive APK audit or physical page-size test.'),
 ('Immutable deterministic WindowEngine / effects / versioned restore','Published list snapshots protected, deterministic kernel tests; full effect/session integration incomplete.'),
 ('Open idempotency / command replay','Ordered epoch/sequence stream and codec v3 tested across Close/recovery; raw Open keys still deduplicate only live windows. No durable acknowledgement or multi-writer log.'),
 ('48dp title / Compact240×180 / Touch320×240','Kernel geometry and authored Compose minima; rendered/AX acceptance unrun.'),
 ('Room restore / debounce≤500ms','AtomicFile geometry still used, not Room. No500ms device persistence bound proved.'),
 ('Hilt / Flow / DataStore / WorkManager / KSP','Flow/coroutines partially used; requested architecture stack incomplete.'),
 ('Isolated web/terminal/preview/format/vault/cast/IME/lens/meet processes','Required complete process/service boundaries absent; dangerous surfaces remain blocked.'),
 ('Version catalog / R8 / benchmarks / SBOM / licenses','Catalog present; release R8, measured profiles and pinned transitive license/SBOM closure absent.'),
 ('Mandatory S1–S30 spikes before gated capability delivery','All30 lack passes; no gate promoted by source or JVM results.'),
 ('M1 / M2 / M3 / M4','Only portions present; H1/commerce, Files/index, alerts/governor and terminal/input milestones unaccepted.'),
 ('M5 / M6 / M7 / M8','Office/browser/vault/media, own casting, multi-device/display/backup and API37 hardening unaccepted.'),
 ('BlackTop60min / own FGS / hotplug / OEM throttling','SH-10/12 implementation and S23/reference-device evidence absent.'),
 ('Mouse learn / controller / external-keyboard IME / confinement','Native keyboard subset only; IN/WM-16 scope incomplete.'),
 ('Five-state thermal / ADPF→sustained→own fallback','Pure policy tested; complete measurement/governor integration absent.'),
 ('Forge registry / macros-off / lifecycle / explicit fidelity loss','Editor bounded encoding/copy/recovery/diff kernels; OOXML container guards only. Complete registry/isolated engines/loss reports absent.'),
 ('SAF safe-save and file-move journal','Editor Save a Copy limited protocol; Files mutations now refuse in backend. No full journal/provider acceptance.'),
 ('Real browser z-order/extensions/security S29/S30','Blocked; no generic WebView substituted as completed browser.'),
 ('Own Beam / trust / secure exclusion / stop / codec/latency indicator','Blocked pending S17–21 and security/hardware review; no delegated system-cast equivalence.'),
 ('AirPlay legal boundary / GPL receiver separate APK','No mirroring/FairPlay claim; counsel/hardware and separate receiver APK absent.'),
 ('Together own-media Syncplay compatibility /≤40ms','Bounded real authenticated TCP exchange and conditional clock intervals passed host/OpenSSL/API34 tests. Pairing, rooms, player/Syncplay integration and multi-device S22/S24 acceptance absent.'),
 ('Lens LN-01–15 / Meet / per-site / PC webcam / recording','Physical camera preview only; S25–28 and processed-track routes absent.'),
 ('No native third-party Android virtual camera/mic claim','Boundary maintained; does not implement own Meet/browser/PC alternatives.'),
 ('Own-Mac Messages / SMS role / Matrix/XMPP / RCS handoff','Transport/role scope incomplete; no no-Mac iMessage, hosted Apple relay or fake RCS API.'),
 ('Encrypted search / source consent / secure-content exclusion','Executed bounded AES-GCM snapshot and locked-session kernel; Android keys/tests authored. No FTS/crawler/source consent integration, Vault/private-content exclusion adapter, key lifecycle or S4 acceptance.'),
 ('Local-first / no default telemetry / opt-in Home / permission-light','Source defaults partly enforced; full privacy/security review unpassed.'),
 ('Keyboard complete / visible focus / TalkBack/Switch/Voice /200%','Selected native interaction regressions pass on API34; no whole-product keyboard/TalkBack/Switch/Voice review.'),
 ('Density changes dimensions, not capability set','Tile geometric impossibility explicitly disabled; full density/AX matrix unrun.'),
 ('$19.99 + $14.99 Pro /14day / signed offline entitlement','Commerce and signed entitlement absent; no fabricated trial/ownership state.'),
 ('Offline no feature loss/no wipe / expiry read-only Files/Find','No remote wipe path introduced; complete entitlement lifecycle absent.'),
 ('Permissive main APK / isolated MPL / no forbidden copyleft assets','No new APK dependency added this pass; optional external host verifiers are not bundled. Full transitive/legal audit still required.'),
 ('Two reference devices / E0 budgets / no invented measurements','API34 emulator startup/Files measurements and855 measured first-party ART rules retained. No two physical reference-device acceptance; budgets remain E0.'),
 ('Requested47 slots vs included48 / dedicated modules','41 exact,2 partial owners,4 absent (quickpanel/office/photos/ime); Meet/GPL ownership also unresolved.'),
 ('Actual Android build and native visual inspection','R16 both-flavor build/tests/lint and74 API34 named cases passed, including actual bounded arithmetic in isolated UID and real authenticated sockets. R17 Tasks native results are revision-specific; full parser containment, visual/AX/physical acceptance absent.')]

def inventory(root=ROOT):
    modules=re.findall(r'"(:[\w:]+)"',(root/'settings.gradle.kts').read_text())
    edges={}; exports={}; sources=[]; packages={}
    for module in modules:
        directory=root/module[1:].replace(':','/')
        build=(directory/'build.gradle.kts').read_text()
        deps=re.findall(r'(\w+)\(project\("(:[\w:]+)"\)\)',build)
        edges[module]={d for kind,d in deps if kind in ('implementation','api','compileOnly','debugImplementation')}
        exports[module]={d for kind,d in deps if kind=='api'}
        for path in sorted((directory/'src/main').rglob('*.kt')):
            text=path.read_text(); match=re.search(r'^package\s+([\w.]+)',text,re.M)
            if not match: raise ValueError(f'No package in {path}')
            package=match.group(1); packages.setdefault(package,set()).add(module)
            sources.append({'path':str(path.relative_to(root)),'module':module,'package':package,
                            'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'text':text})
    return modules,edges,exports,sources,packages

def reachable(module,edges,exports):
    result={module}; pending=list(edges[module])
    while pending:
        dep=pending.pop()
        if dep not in result:
            result.add(dep); pending.extend(exports[dep])
    return result

def missing_edges(edges,exports,sources,packages):
    missing=[]
    for source in sources:
        visible=reachable(source['module'],edges,exports)
        # Package strings (e.g. macrobenchmark targets) are not compile imports.
        # String interpolation/nested-comment semantics are outside this lexical scanner.
        code=re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/', '', source['text'])
        references=set(re.findall(r'\bdev\.fusion(?:\.[A-Za-z_]\w*)+',code))
        for reference in references:
            owners=[p for p in packages if reference==p or reference.startswith(p+'.')]
            if not owners: continue # Generated/platform/unknown symbols require a real compiler, not guesses.
            package=max(owners,key=len)
            if not packages[package] & visible:
                missing.append({'source':source['path'],'reference':reference,'owners':sorted(packages[package])})
    return missing

def report(root=ROOT):
    modules,edges,exports,sources,packages=inventory(root)
    missing=missing_edges(edges,exports,sources,packages)
    if missing: raise ValueError(json.dumps(missing,indent=2))
    rows=json.loads((root/'docs/feature-register.json').read_text())
    lines=['# Full recorded v2/v2.1 scope review — R11 continuation','',
      '**231 requirement records and36 cross-cutting obligations reviewed against the recorded scope; no whole feature or module accepted.**',
      'This is a traceability/ownership/gap audit, not a claim that every line of Kotlin was semantically verified. The recorded owner register is the source of scope; this does not independently prove that its transcription includes every sentence of the original specifications.',
      '',f'{len(modules)} included modules; {len(sources)} main Kotlin files inventoried with hashes. Internal source-package references checked against direct dependencies and transitive `api` exports. Reflection, generated code, external dependencies, public-signature type closure and Android/Gradle DSL compilation remain outside this scanner.',
      '', '## Cross-cutting contract (v2.1 supersedes conflicting v2.0 clauses)','', '| Obligation | Actual frontier / remaining gap |','|---|---|']
    lines += [f'| {name} | {gap} |' for name,gap in FOUNDATIONS]
    lines += ['', '## Every recorded requirement','',
      'Owner candidates below identify where work belongs, not implementation completeness. An absent owner is explicit. `partial_unverified` retains the entire remaining acceptance obligation; executed kernel subcontracts do not promote the row.',
      '', '| ID | Full recorded target | Status | Owner candidates | Mandatory spikes |', '|---|---|---|---|---|']
    for row in rows:
        candidates=FAMILIES[row['id'].split('-')[0]]
        owners=', '.join('`:'+p.replace('/',':')+'`'+(' (absent)' if ':'+p.replace('/',':') not in modules else '') for p in candidates)
        lines.append(f"| {row['id']} | {row['feature'].replace('|','/')} | {row['status']} | {owners} | {', '.join(row['gates']) or 'No spike named here; integration/security/AX gates still apply'} |")
    lines += ['', 'Status counts: '+json.dumps(dict(sorted(Counter(r['status'] for r in rows).items())))+'.', '',
      'Reproduce: `python3 tools/native_source_audit.py --check`. Fingerprints in `docs/verification/r11-source-inventory.json` identify reviewed inputs, not compiled artifacts. See `R11-ENGINEERING.md` for mechanisms, adversaries, evidence and the next falsifying tests.', '']
    data={'kind':'source ownership only; NOT Android compilation','modules':modules,
          'production_project_edges':{k:sorted(v) for k,v in edges.items()},'missing_internal_edges':missing,
          'source_inventory':[{k:v for k,v in s.items() if k!='text'} for s in sources]}
    return '\n'.join(lines),json.dumps(data,indent=2)+'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    text,data=report()
    for name,content in [('docs/FULL-SPEC-REVIEW.md',text),('docs/verification/r11-source-inventory.json',data)]:
        path=ROOT/name
        if args.check:
            if path.read_text()!=content: raise ValueError('Stale scope/source review: '+name)
        else: path.write_text(content)
    print('231 recorded requirements,36 cross-cutting obligations and all native main-source package ownership checked. NOT a build or completeness proof.')
if __name__=='__main__': main()
