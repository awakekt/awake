# Keep rules every app that depends on this module gets, so a shrunk and obfuscated release (ProGuard
# on desktop, R8 on Android) keeps working with cryptography-kotlin, which this module uses.

# The JDK provider reaches BouncyCastle only when an app puts it on the classpath, and checks for it
# first, so its references to BouncyCastle are optional and a release without it is fine.
-dontwarn org.bouncycastle.**

# The providers are found with ServiceLoader, through META-INF/services/<interface name> files that
# name each provider class. Renaming either one leaves CryptographyProvider.Default with no provider.
-keep interface dev.whyoleg.cryptography.CryptographyProviderContainer
-keep class * implements dev.whyoleg.cryptography.CryptographyProviderContainer {
    <init>();
}
