package com.fares.glass.sys;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.Date;

import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

/**
 * Builds the self-signed ADB certificate. This lives in Java because Kotlin
 * compiles against the desktop JDK, which hides the JDK-only sun.security.x509. Java files
 * are compiled against android.jar, and sun-security-android lives in android.sun.security.
 */
public final class CertUtil {
    private CertUtil() {}

    public static Certificate selfSigned(KeyPair kp) throws Exception {
        String alg = "SHA512withRSA";
        X500Name name = new X500Name("CN=Glass");
        X509CertInfo info = new X509CertInfo();
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        info.set(X509CertInfo.SERIAL_NUMBER,
                new CertificateSerialNumber(BigInteger.valueOf(System.nanoTime() & 0x7fffffffL)));
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get(alg)));
        info.set(X509CertInfo.SUBJECT, name);
        info.set(X509CertInfo.ISSUER, name);
        info.set(X509CertInfo.KEY, new CertificateX509Key(kp.getPublic()));
        info.set(X509CertInfo.VALIDITY, new CertificateValidity(
                new Date(), new Date(System.currentTimeMillis() + 3650L * 86_400_000L)));
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(kp.getPrivate(), alg);
        return cert;
    }
}
