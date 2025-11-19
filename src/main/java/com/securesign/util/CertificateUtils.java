package com.securesign.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

@Component
public class CertificateUtils {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public KeyStore createTestKeyStore() throws Exception {
        // RSA Anahtar Çifti Oluştur
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Self-Signed Sertifika Oluştur
        X500Name issuer = new X500Name("CN=Test Signer, O=SecureSign, C=TR");
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)); // 1 yıl geçerli

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, issuer, keyPair.getPublic());

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(contentSigner);
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);

        // KeyStore'a kaydet
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("alias", keyPair.getPrivate(), "password".toCharArray(), new java.security.cert.Certificate[]{certificate});

        return keyStore;
    }
}

