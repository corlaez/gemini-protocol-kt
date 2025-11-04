package com.corlaez.server

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import java.io.FileReader
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

// Register Bouncy Castle provider
private val unit: Unit = run {
    Security.addProvider(BouncyCastleProvider())
}

/** Load PEM certificates from a file */
internal fun loadPEMCertificates(certPath: String): List<X509Certificate> {
    val certificates = mutableListOf<X509Certificate>()

    PEMParser(FileReader(certPath)).use { parser ->
        var obj = parser.readObject()
        while (obj != null) {
            when (obj) {
                is X509Certificate -> certificates.add(obj)
                is X509CertificateHolder -> {
                    val cert = JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate(obj)
                    certificates.add(cert)
                }
            }
            obj = parser.readObject()
        }
    }
    return certificates
}

/** Load PEM private key from a file */
internal fun loadPEMPrivateKey(keyPath: String): PrivateKey {
    PEMParser(FileReader(keyPath)).use { parser ->
        val obj = parser.readObject()
        val converter = JcaPEMKeyConverter().setProvider("BC")

        return when (obj) {
            is PEMKeyPair -> {
                converter.getKeyPair(obj).private
            }
            is PKCS8EncryptedPrivateKeyInfo -> {
                // Handle encrypted key (requires password)
                throw IllegalArgumentException("Encrypted private keys not supported yet")
            }
            is PrivateKeyInfo -> {
                converter.getPrivateKey(obj)
            }
            else -> throw IllegalArgumentException("Unsupported key format")
        }
    }
}

/** Used by self-signed and let's encrypt certificates */
internal fun createSSLContextFromKeyStore(keyStore: KeyStore): SSLContext {
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, CharArray(0))

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.keyManagers, null, null)

    // Configure to only allow TLS 1.2 and 1.3
    val sslParameters = sslContext.defaultSSLParameters
    sslParameters.protocols = arrayOf("TLSv1.2", "TLSv1.3")

    return sslContext
}

/** Generate a self-signed certificate for local testing */
internal fun selfSignedCertificate(hostname: String): KeyStore {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()

    val now = Instant.now()
    val notBefore = Date.from(now)
    val notAfter = Date.from(now.plus(365, ChronoUnit.DAYS))

    val subject = X500Name("CN=$hostname")
    val serialNumber = BigInteger.valueOf(now.toEpochMilli())

    val certificateBuilder = X509v3CertificateBuilder(
        subject,
        serialNumber,
        notBefore,
        notAfter,
        subject,
        SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
    )

    val signer = JcaContentSignerBuilder("SHA256WithRSA")
        .setProvider("BC")
        .build(keyPair.private)

    val certificateHolder = certificateBuilder.build(signer)
    val certificate = JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certificateHolder)

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setKeyEntry(
        "gemini",
        keyPair.private,
        CharArray(0),
        arrayOf(certificate)
    )

    return keyStore
}

/** Create a let's encrypt certificate for production */
internal fun letsEncryptCertificate(keyPath: String, fullchainPath: String): KeyStore {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)

    val privateKey = loadPEMPrivateKey(keyPath)
    val fullchain = loadPEMCertificates(fullchainPath)// (certificate + intermediates)

    if (fullchain.isEmpty()) {
        throw IllegalArgumentException("No certificates found in fullchain file: $fullchainPath")
    }

    keyStore.setKeyEntry(
        "gemini",
        privateKey,
        CharArray(0),
        fullchain.toTypedArray()
    )
    return keyStore
}
