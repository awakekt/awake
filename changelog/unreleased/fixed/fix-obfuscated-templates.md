- **A shrunk release of an app on `awake:project` builds and finds its crypto provider again.** Since content bundle encryption (#1153), `awake:project` uses cryptography-kotlin, whose JDK provider refers to BouncyCastle for when an app ships it. Against Core 0.29.0, a release that shrinks with ProGuard (desktop) or R8 (Android) fails with unresolved `org.bouncycastle` references, and once those are silenced, an obfuscated release finds no crypto provider at run time when it decrypts a bundle or checks a plugin signature. `awake:project` and `awake:plugin-archive` now ship consumer keep rules for both. On 0.29.0, add the same lines to the app's own `proguard-rules.pro`:

  ```
  -dontwarn org.bouncycastle.**
  -keep interface dev.whyoleg.cryptography.CryptographyProviderContainer
  -keep class * implements dev.whyoleg.cryptography.CryptographyProviderContainer { <init>(); }
  ```
