import { useState } from "react";
import { ArrowRight, Check, Copy, Folder, Info, Terminal } from "lucide-react";

const files = [
  "android/settings.gradle.kts (45 modules)",
  "android/app/.../MainActivity.kt (window host + external display)",
  "android/feature/beam/.../LocalMediaServer.kt (RFC 7233 byte-range)",
  "android/format/psd/.../PsdCompositeEngine.kt (PackBits RLE)",
  "android/core/files/.../TrashRepository.kt (atomic journal)",
  "android/core/storage/.../FusionDb.kt (framework SQLite)",
  "android/core/security/.../PasswordCrypto.kt + tests",
  "android/feature/capture/.../ScreenRecordService.kt (Android 14 FGS)",
  "android/feature/alerts/.../FusionNotificationListener.kt",
  "android/cast/dlna/.../DlnaClient.kt (SSDP + SOAP AVTransport)",
  "android/benchmark/.../StartupBenchmark.kt (Macrobenchmark)",
  "android/README.md + android/STATUS.md (authoritative ledger)",
];

export default function App() {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText("android/");
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };

  return (
    <main className="min-h-screen bg-[#0b0e10] text-zinc-200">
      <div className="mx-auto max-w-5xl px-6 pb-24 pt-9 md:px-10">
        <nav className="flex items-center justify-between border-b border-white/10 pb-6">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center bg-emerald-400 text-xl font-black text-zinc-950">F</span>
            <span className="text-sm font-bold uppercase tracking-[0.18em] text-white">Fusion Shell</span>
          </div>
          <span className="font-mono text-xs text-zinc-500">ANDROID SOURCE / 0.1.0</span>
        </nav>

        <section className="max-w-3xl pt-20 md:pt-28">
          <div className="mb-5 font-mono text-xs uppercase tracking-[0.22em] text-emerald-400">Android Studio project · work in progress</div>
          <h1 className="text-5xl font-semibold tracking-tight text-white md:text-7xl">Fusion Shell</h1>
          <p className="mt-7 max-w-2xl text-lg leading-relaxed text-zinc-400">
            The real Kotlin/Gradle source is in <code className="font-mono text-emerald-300">android/</code>.
            It is a narrow launcher baseline, not the finished v2.1 suite. The earlier TypeScript
            snippet exporter and 100% completion claims have been removed.
          </p>
          <div className="mt-10 flex flex-wrap items-center gap-4">
            <button onClick={copy} className="flex items-center gap-2 bg-emerald-400 px-5 py-3 text-sm font-semibold text-zinc-950 hover:bg-emerald-300">
              {copied ? <Check size={17} /> : <Copy size={17} />}
              {copied ? "Path copied" : "Copy project path"}
            </button>
            <a href="#setup" className="flex items-center gap-2 text-sm text-zinc-300 hover:text-white">Open in Android Studio <ArrowRight size={16} /></a>
          </div>
        </section>

        <div className="my-20 h-px bg-white/10" />

        <section className="grid gap-12 md:grid-cols-[1fr_1fr]">
          <div>
            <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-zinc-500"><Folder size={15} /> Implemented codebase (R13)</div>
            <ul className="space-y-4 text-sm leading-relaxed text-zinc-300">
              <li>R13 correctness pass: fixed 6 compile/runtime defects (uncallable Json extensions, String-for-Int args, a PSD test that could never pass, cleartext blocking all casting).</li>
              <li>REAL castv2 client (TLS :8009, hand-rolled protobuf CastMessage, loopback-tested) + REAL AirPlay /play sender + REAL SMS via Telephony/SmsManager.</li>
              <li>Window manager, SAF files with reversible trash, embedded byte-range media server, DLNA SSDP + AVTransport, PSD RLE engine.</li>
              <li>Android 14 MediaProjection capture, NotificationListenerService alerts, PBKDF2/AES-GCM backup, CameraX preview, external-display mirror.</li>
              <li>Macrobenchmark + baselineprofile modules, managed-device CI, JVM tests across codec/JSON/protocol/fixtures.</li>
            </ul>
          </div>
          <div>
            <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-zinc-500"><Info size={15} /> Honest progress</div>
            <div className="border-l-2 border-emerald-400 pl-5">
              <p className="text-3xl font-semibold text-white">0 / 48</p>
              <p className="mt-1 text-sm text-zinc-400">modules fully complete and Android-build/device verified <span className="text-zinc-600">(0%)</span></p>
            </div>
            <div className="mt-7 border-l-2 border-emerald-400 pl-5">
              <p className="text-3xl font-semibold text-white">47 / 48</p>
              <p className="mt-1 text-sm text-zinc-400">modules with real source present <span className="text-zinc-600">(97.9%)</span>; 1 separate-repo GPL receiver <span className="text-zinc-600">(2.1%)</span></p>
            </div>
            <p className="mt-7 text-sm leading-relaxed text-amber-200/80">No Android SDK or emulator was available here. No APK, Gradle sync, JVM test or hardware benchmark was run. Counts are source-present only; 0 modules are build/device verified. See android/STATUS.md.</p>
          </div>
        </section>

        <section id="setup" className="mt-20 border-t border-white/10 pt-10">
          <div className="mb-5 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-emerald-400"><Terminal size={15} /> Try it on a laptop</div>
          <ol className="max-w-3xl space-y-4 text-sm leading-relaxed text-zinc-300">
            <li><span className="mr-3 font-mono text-emerald-300">01</span>In Android Studio, use File &gt; Open and select the <code className="font-mono text-white">android/</code> directory, not the website root.</li>
            <li><span className="mr-3 font-mono text-emerald-300">02</span>Install SDK Platform 35 and use JDK 17. Configure local Gradle 8.9, or generate the missing wrapper binary with an installed Gradle 8.9.</li>
            <li><span className="mr-3 font-mono text-emerald-300">03</span>Sync and run <code className="font-mono text-white">:app:assembleDebug</code> on an Android 14+ emulator. Run <code className="font-mono text-white">:core:windowing:test</code> for reducer tests.</li>
            <li><span className="mr-3 font-mono text-emerald-300">04</span>Open Files, grant one folder, and select a .txt or .md file to edit. Read <code className="font-mono text-white">android/README.md</code> and <code className="font-mono text-white">android/STATUS.md</code> first.</li>
          </ol>
        </section>

        <section className="mt-16 border-t border-white/10 pt-8">
          <div className="mb-4 font-mono text-xs uppercase tracking-widest text-zinc-500">Source paths</div>
          <div className="grid gap-x-8 gap-y-2 md:grid-cols-2">
            {files.map((path) => <div key={path} className="overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs text-zinc-500">{path}</div>)}
          </div>
        </section>
      </div>
    </main>
  );
}