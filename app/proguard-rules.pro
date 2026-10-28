# libadb-android
-keep class io.github.muntashirakon.adb.** { *; }

# sun-security-android (package is android.sun.security, not sun.security)
-keep class android.sun.security.** { *; }

# Conscrypt (optional TLS provider used by libadb)
-keep class org.conscrypt.** { *; }

# BouncyCastle (certificate for the ADB key)
-keep class org.bouncycastle.** { *; }

# Suppress warnings for classes that may be missing at runtime
-dontwarn android.sun.security.**
-dontwarn sun.security.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
