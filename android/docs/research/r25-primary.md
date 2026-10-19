# R25 fresh primary-source ledger —2026-10-18

Scope: source research, not runtime or legal acceptance. No independent human expert/prover is implied. Search excerpts are marked; full pages are distinguished. Historical R24C sources retain their own retrieval/evidence boundaries.

## Tails / hardware / identity

**Fetched current warnings, both chunks0–1 of2:** Tails distinguishes identity/metadata leakage, Tor correlation limits and host firmware/hardware trust. It advises metadata cleaning before sharing and keeping separate activities/sessions. This is evidence for a bounded metadata-copy feature and explicit session separation—not proof an Android APK can become Tails or make visible content anonymous. [1](https://tails.net/doc/about/warnings/index.en.html)

**Primary search excerpts:** Tails' untrusted-computer warning explicitly excludes altered hardware/firmware and describes a physical keyboard logger recording before software can protect it. A screen keyboard avoids the physical keyboard path only; it does not defeat pointer/screen/camera/host observation. [2](https://tails.net/doc/about/warnings/computer/index.en.html) [4](https://tails.net/doc/first_steps/accessibility/)

Required distinction: a malicious inline logger and a clean keyboard can produce the same app-visible device/event trace. A classifier restricted to that trace cannot establish which world it is in. Input-change callbacks therefore support conservative discard/rearm, not passive-logger detection. No user laptop firmware or USB policy was changed in this sandbox.

## USB policy / authority frontier

**Primary search excerpts:** USBGuard evaluates ordered authorization rules and defaults to blocking unmatched devices. Its policy generator allows currently connected devices, so executing it on an untrusted initial inventory can bless an attacker; generating policy is not authentication or enforcement. Its configuration distinguishes present-device/controller policy and warns that restoring controller defaults on daemon shutdown can re-enable permissive behavior. [1](https://usbguard.github.io/documentation/rule-language) [2](https://usbguard.github.io/documentation/configuration)

Consequence: any OSB host-policy helper must be read-only by default, refuse missing inventory, distinguish desired policy from actual enforcement, and require separate explicit authority/physical validation. VID/PID/port/hash matching does not prove a trustworthy physical device or defeat invisible preexisting interception. No USBGuard daemon, privileged host policy or hardware attack experiment was run by this research step.

## Android input/display protections

**Primary search excerpts:** full and partial tapjacking need distinct handling. `setFilterTouchesWhenObscured` covers full occlusion; partial occlusion requires explicit `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` treatment. `HIDE_OVERLAY_WINDOWS` concerns app overlays; trusted accessibility and other activity/OS attacks remain separate. [1](https://developer.android.com/privacy-and-security/risks/tapjacking) [2](https://developer.android.com/security/fraud-prevention/activities) [3](https://developer.android.com/guide/components/activities/secure-bal)

Existing actual no-INTERNET, secure-window, obscuration, focus/input discard and stale-control tests are retained. None becomes hostile-host or firmware protection by adding a permission flag.

**Primary search excerpt:** programmatic keyboard hiding is best effort and may be ignored; a focused editable target is needed for showing. Thus a requested IME action is not an observed UI condition. Read actual insets and validate the resulting behavior rather than assuming the request succeeded. [12](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/SoftwareKeyboardController)

## Streaming rights frontier

**Fresh primary search excerpts:** Spotify Web Playback SDK requires eligible Premium subscriptions and prior written approval for commercial projects. Developer Policy distinguishes limited widgets/previews from full streaming and applies commercial restrictions. This is not an unrestricted free-for-all-use service integration merely because a client is open source. [1](https://developer.spotify.com/documentation/web-playback-sdk) [4](https://developer.spotify.com/policy) [11](https://developer.spotify.com/documentation/embeds)

Consequences: preserve the honest external-handoff labeling; do not counterfeit playback, bypass entitlement/DRM or count local WAV tests as Spotify/YouTube/SoundCloud integration. This research does not conclude that every permitted embed is impossible; exact service terms, app identity, user rights and actual runtime need separate approval/evidence.

## Research limits

No novelty or exhaustive-frontier claim; no HCI preference study, physical threat lab, independent licensing opinion, Tor deployment, native browser load or Office runtime has occurred in this R25 research step. Current guidance and component availability are hypotheses/inputs for concrete tests, not completed product modules.


## Bounded metadata copy frontier

**Primary search excerpts:** Tails' metadata guide warns that complex formats cannot have all metadata reliably removed; its Dangerzone comparison distinguishes metadata cleaning from hostile-document sanitization and says neither removes identifying visual content/fingerprints. This supports a clearly bounded new-copy tool, not an "anonymous file" badge. [1](https://tails.net/doc/sensitive_documents/metadata/index.en.html) [5](https://tails.net/doc/persistent_storage/additional_software/dangerzone/index.en.html)

**Primary specification excerpts:** PNG Third Edition defines critical/ancillary chunk bits, CRC/order and consecutive IDAT; modern colour priority includes cICP/iCCP/sRGB/cHRM+gAMA. A parser cannot blindly drop every ancillary chunk and promise unchanged rendering. A narrow RGB/RGBA8 contract, full zlib/framing verification, explicit profile refusal and preserved-image-data comparison is proposed before implementation. [5](https://www.w3.org/TR/png-3/) [6](https://w3.org/TR/PNG)

## Media threading and provider interface

**Primary API excerpts:** MediaPlayer is explicitly not thread-safe: creation and all access belong on one thread, with a Looper for callbacks. Moving only some calls to a worker is not automatically a safe fix. `prepareAsync` does not turn all earlier resolver/data-source calls into asynchronous work. The current main-thread provider boundary remains named, not called15s bounded. [1](https://developer.android.com/reference/kotlin/android/media/MediaPlayer) [2](https://developer.android.com/media/platform/mediaplayer/basics) [9](https://developer.android.com/guide/components/processes-and-threads)

Candidate mechanisms remain distinct: (a) single-owner player looper with an explicit blocked-worker resource envelope; (b) separately revocable process/session; (c) a reviewed maintained media library with actual data-source/decoder tests. No such migration or video decoder output was executed by this research step.
