# Privacy requirement refinement, not an anonymous-system delivery

Fresh-source basis: [R22 primary research](research/r22-primary.md). These explicit requirements refine future acceptance; they do not mark the original231 requirements/30 formal spikes complete or inflate the module census.

| Requirement | Concrete artifact/status | Acceptance still required |
|---|---|---|
| Read/import-copy rather than silently retaining broad source authority | GET_CONTENT, content-only bounded read, no persisted grant, no source output open | API36/37/OEM/provider-revocation/recreation and delayed-IO tests; malicious provider aliasing is not defeated by URI separation |
| Explicit lossy data minimization | All selected cells displayed escaped; new literal workbook from selected values only; static loss report names discarded types/layout/metadata | User must review identifiers in retained values, filenames and sharing context; not a metadata cleaner for arbitrary files |
| Parse attacker packages outside trusted UI process | Owned synced readonly/unlinked FD and isolated worker; ZIP/XML/workbook admission; data-only reply | Memory/time/physical-device coverage, crash recovery and compromised-child semantics remain separate |
| No 'RAM-only' or secure-delete claim for disk capture | UI warns temporary private disk file before unlink | No storage-remanence or privileged-host secrecy guarantee |
| Browser permission liveness must follow actual renderer lifecycle | Suspension kernel revokes old prompts/grants | Audited engine crash/kill/frame/navigation bridge and authentic consent UI absent |
| Keep offline companion separate from network-enabled main app | Existing no-INTERNET APK unchanged; no fresh privacy run inferred | Whole-device/host/IME/network anonymity is not implied by one APK's manifest |
| Future networking must fail closed in its declared threat model | Planned only: explicit routing status, DNS/IPv6/bypass/reconnect/captive-portal attacks, no silent cloud relay | No VPN/Tor integration, global routing or fingerprint-resistance claim |
| Honest host and external-record boundary | Documentation and scoped evidence policy | Cannot hide this conversation, GitHub, provider records, laptop host or other applications retroactively |
| Honest lightweight comparison | No new Office/browser engine bundled | Measure complete guest+host RSS/storage/startup/power/workload before any 'lighter than Tails' comparison; APK size is insufficient |

An optional privacy view is not a replacement for the required native compositor, browser or Office engine. No generic WebView, delegated system cast, VPN badge or offline scratchpad is promoted to that scope. Default local-first/no account/no ads/no telemetry principles remain; this work does not audit every pre-existing path.
