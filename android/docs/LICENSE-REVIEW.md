# Dependency / license review — incomplete, release blocked

This is an engineering evidence record, not legal advice or a completed SBOM.
No AirPlay/LOKit/GeckoView/ML/codec implementation or model weights were imported
in R1. The document engine uses Kotlin/JDK/Android framework APIs and the existing
AndroidX/coroutines dependency families. All coordinates are in one catalog.

## Files actually retrieved on 2026-10-13

Retrieved through GitHub's contents API and retained verbatim in `licenses/`:

| File | Source revision/path | Scope |
|---|---|---|
| `gradle-LICENSE.txt` | `gradle/gradle`, tag `v8.13.0`, `LICENSE` | Apache-2.0 for Gradle; file also contains distribution dependency notices |
| `androidx-LICENSE.txt` | `androidx/androidx`, branch `androidx-main`, `LICENSE.txt` | Apache-2.0 repository-level text; **not** every pinned AAR/transitive dependency |
| `kotlinx.coroutines-LICENSE.txt` | `Kotlin/kotlinx.coroutines`, tag `1.9.0`, `LICENSE.txt` | Apache-2.0 including JetBrains attribution |

SHA-256 of those exact files:

```text
gradle: 9536d88ea948603d18e232a13f5958d67807cd80828036b082bff171d2cf0703
androidx: 809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0
coroutines: b1febe6399dffb10d19d35e7663ab16300c93cb0476a94115df1cc0097a8ffd8
```

Gradle's file includes BSD/MIT/CDDL/LGPL and other notices for its full **build
runtime**. Those tools are not Android app dependencies. Do not mistake copying
that notice file for bundling those libraries in the APK. Runtime and build/test
classpaths need separate inventories. The upstream root `NOTICE` lookup returned
404; no missing notice was invented.

## Committed wrapper provenance

Unmodified `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` from
`gradle/gradle` tag `v8.13.0`, retrieved using the GitHub contents API:

```text
gradlew: c76f16038b4e70b84ed0b3b9e08e82a8ecaf66ec0efdfdecdece92520fa3cecb
gradlew.bat: 0f3ed8f03b50934cb8c48b15a470d5c20a30a5385825e48b55bcc8ea3d8f8e18
JAR: 81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
```

CI adds upstream wrapper validation. The wrapper distribution URL is pinned to
8.13 over HTTPS. A distribution SHA-256 was **not** independently retrieved in
this sandbox; no guessed checksum is included. Add a verified official checksum
before release supply-chain approval. No downloaded JDK/compiler/distribution
archive is committed.

## Unclosed release checks

- Resolve both `play` and `pro` runtime dependency graphs on an SDK-enabled host.
- Produce a complete SBOM including transitive libraries and future native libs /
  model weights; inspect each artifact's actual LICENSE/NOTICE and source offer.
- Verify the Kotlin runtime and every pinned AndroidX AAR version; repository
  umbrella text is not a substitute for individual attribution/notice review.
- Compare the resolved license inventory in CI and block unknown or prohibited
  runtime licenses; generate in-app notices and required source offers.
- Confirm no Cast SDK leaks into pro; no GPL/AGPL/SSPL/x265/FairPlay-derived code
  leaks into the main APK. Current runtime adds none of those dependencies.
- Keep receiver GPL APK and any Wine payload separate. S19 requires counsel,
  independently of whether sender code might carry a permissive license.

`tools/requirements.py --release` and release Gradle tasks intentionally reject
this incomplete state. A successful debug assemble is source evaluation only,
not permission to publish the product.
