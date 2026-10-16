import { useState } from "react";
import { ArrowRight, Check, Copy, FileCode2, Info, Shield, Terminal } from "lucide-react";

const files = [
  "android/privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt",
  "android/privacy/src/main/java/dev/fusion/privacy/DevicePrivacySnapshot.kt",
  "android/privacy/src/androidTest/java/dev/fusion/privacy/PrivacyPostureTest.kt",
  "android/app/src/main/java/dev/fusion/shell/MainActivity.kt",
  "android/format/ooxml/.../LiteralXlsxImport.kt (bounded, lossy projection)",
  "android/format/pdf/.../FormatWorkerService.kt (isolated-worker subset)",
  "android/docs/R23-ENGINEERING.md (A–J engineering record)",
  "android/docs/R23-PRIVACY-PRD.md (scoped proposal and limits)",
];

export default function App() {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText("android/");
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#0b0e10] text-zinc-200">
      <div className="mx-auto max-w-5xl px-6 pb-24 pt-9 md:px-10">
        <nav className="flex items-center justify-between border-b border-white/10 pb-6">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center bg-emerald-400 text-xl font-black text-zinc-950">F</span>
            <span className="text-sm font-bold uppercase tracking-[0.18em] text-white">Fusion Shell</span>
          </div>
          <span className="font-mono text-xs text-zinc-500">NATIVE ANDROID SOURCE / R23</span>
        </nav>

        <section className="max-w-3xl pt-20 md:pt-28">
          <div className="mb-5 font-mono text-xs uppercase tracking-[0.22em] text-emerald-400">Android Studio Rabbit1 · native Kotlin project</div>
          <h1 className="text-5xl font-semibold tracking-tight text-white md:text-7xl">Fusion Shell</h1>
          <p className="mt-7 max-w-2xl text-lg leading-relaxed text-zinc-400">
            Open <code className="font-mono text-emerald-300">android/</code> in Android Studio to build the real Kotlin/Jetpack Compose application.
            The root Vite page is only a project guide. The full v2.0/v2.1 suite is still incomplete; no release, performance, or global-anonymity claim is made.
          </p>
          <div className="mt-10 flex flex-wrap items-center gap-4">
            <button onClick={copy} className="flex items-center gap-2 bg-emerald-400 px-5 py-3 text-sm font-semibold text-zinc-950 hover:bg-emerald-300">
              {copied ? <Check size={17} /> : <Copy size={17} />}
              {copied ? "Path copied" : "Copy project path"}
            </button>
            <a href="#setup" className="flex items-center gap-2 text-sm text-zinc-300 hover:text-white">Rabbit1 setup and try-out <ArrowRight size={16} /></a>
          </div>
        </section>

        <div className="my-20 h-px bg-white/10" />

        <section className="grid gap-12 md:grid-cols-[1fr_1fr]">
          <div>
            <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-zinc-500"><Shield size={15} /> R23 privacy increment</div>
            <ul className="space-y-4 text-sm leading-relaxed text-zinc-300">
              <li>A separate offline companion with no INTERNET permission, volatile scratch text, screenshot exclusion, and blocked clipboard/rich-content routes.</li>
              <li>New local posture panel reports bounded Android observations (current IME, selected system settings, reported accessibility services and input devices); unavailable values stay unknown.</li>
              <li>Keyboard, accessibility, developer and security controls open Android Settings. The app never changes system settings or reports USB Protection as enabled.</li>
              <li>Explicitly not a malware/keylogger scan, Tails replacement, VPN, hardware/firmware attestation, or secure-delete guarantee. See the R23 PRD and A–J report.</li>
            </ul>
            <div className="mt-6 border-l-2 border-amber-400 pl-5 text-sm leading-relaxed text-amber-100/80">
              R23 Kotlin and Android tests were not compiled or run in this sandbox. No R23 APK, emulator result, physical-device, USB, or Rabbit1 GUI test is claimed.
            </div>
          </div>
          <div>
            <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-zinc-500"><Info size={15} /> Honest module counts</div>
            <div className="border-l-2 border-emerald-400 pl-5">
              <p className="text-3xl font-semibold text-white">48 / 49</p>
              <p className="mt-1 text-sm text-zinc-400">modules meet the repository's substantial testable-subset standard <span className="text-zinc-600">(97.96%)</span></p>
            </div>
            <div className="mt-7 border-l-2 border-amber-400 pl-5">
              <p className="text-3xl font-semibold text-white">0 / 49</p>
              <p className="mt-1 text-sm text-zinc-400">modules fully specification-accepted <span className="text-zinc-600">(0%)</span></p>
            </div>
            <p className="mt-7 text-sm leading-relaxed text-zinc-500">The remaining 1/49 subset is not established. These are module-level census numbers, not feature completion or current-patch runtime verification. See <code className="font-mono">android/docs/MODULE-TESTABILITY.md</code>.</p>
          </div>
        </section>

        <section id="setup" className="mt-20 border-t border-white/10 pt-10">
          <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-emerald-400"><Terminal size={15} /> Try it on a laptop</div>
          <ol className="max-w-3xl space-y-4 text-sm leading-relaxed text-zinc-300">
            <li><span className="mr-3 font-mono text-emerald-300">01</span>Open the repository's <code className="font-mono text-white">android/</code> directory in Android Studio Rabbit1 (not the root web page).</li>
            <li><span className="mr-3 font-mono text-emerald-300">02</span>Use JDK 17 and the pinned AGP 8.13.2 / Kotlin 2.2.21 / SDK 36 configuration. Compile/target36 still deviate from the requested 37 baseline.</li>
            <li><span className="mr-3 font-mono text-emerald-300">03</span>On a disposable API34+ emulator, assemble <code className="font-mono text-white">:privacy:assembleDebug</code>, or use the Studio <code className="font-mono text-white">privacy</code> run configuration.</li>
            <li><span className="mr-3 font-mono text-emerald-300">04</span>Review the Local device posture panel and Settings links. Use dummy text: switching to Settings or another app discards the scratchpad by design.</li>
            <li><span className="mr-3 font-mono text-emerald-300">05</span>Read <code className="font-mono text-white">android/docs/BUILD.md</code> for host-only source checks and the required Android instrumentation command. Host checks do not replace Android execution.</li>
          </ol>
        </section>

        <section className="mt-16 border-t border-white/10 pt-8">
          <div className="mb-4 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-zinc-500"><FileCode2 size={15} /> Source paths</div>
          <div className="grid gap-x-8 gap-y-2 md:grid-cols-2">
            {files.map((path) => <div key={path} className="overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs text-zinc-500">{path}</div>)}
          </div>
        </section>
      </div>
    </main>
  );
}
