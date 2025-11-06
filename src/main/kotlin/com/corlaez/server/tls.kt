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
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder
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

/** Takes any KeyStore and creates an SSLContext for TLS 1.2 an 1.3 */
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
internal fun generatedSelfSignedCertificate(hostname: String): KeyStore {
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
        "generatedSelfSigned",
        keyPair.private,
        CharArray(0),
        arrayOf(certificate)
    )

    return keyStore
}

internal fun loadKeyStoreFromFiles(privateKeyPath: String, certPemPath: String, privateKeyPassword: CharArray): KeyStore {
    // Load private key (with optional password)
    val privateKey = loadPEMPrivateKey(
        privateKeyPath,
        password = if (privateKeyPassword.isEmpty()) null else privateKeyPassword
    )

    // Load certificate(s)
    val certificates = loadPEMCertificates(certPemPath)

    if (certificates.isEmpty()) {
        throw IllegalArgumentException("No certificates found in file: $certPemPath")
    }

    // Create an ephemeral Keystore in memory
    // Note: The password here is purely temporary, only for creating the in-memory keystore instance
    val tempPassword = "temp_password_bc".toCharArray()
    val keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME)
    keyStore.load(null, tempPassword)
    // Add the loaded key and certificate to the in-memory Keystore
    keyStore.setKeyEntry(
        "pemFileBased",
        privateKey,
        tempPassword, // Key password is the same as temp store password
        certificates.toTypedArray()
    )
    return keyStore
}

/** Load PEM certificates from a file. Handles both single certificates and certificate chains */
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

/** Load PEM private key from a file. Supports encrypted and unencrypted keys */
internal fun loadPEMPrivateKey(keyPath: String, password: CharArray? = null): PrivateKey {
    PEMParser(FileReader(keyPath)).use { parser ->
        val obj = parser.readObject()
        val converter = JcaPEMKeyConverter().setProvider("BC")

        return when (obj) {
            is PEMKeyPair -> {
                // Unencrypted private key
                converter.getKeyPair(obj).private
            }
            is PKCS8EncryptedPrivateKeyInfo -> {
                if (password == null) {
                    throw IllegalArgumentException(
                        "Private key at $keyPath is encrypted but no password was provided"
                    )
                }
                // JcePKCSPBEInputDecryptorProviderBuilder
                val decryptorBuilder = JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider("BC")
                val inputDecryptorProvider = decryptorBuilder.build(password)
                val privateKeyInfo = obj.decryptPrivateKeyInfo(inputDecryptorProvider)
                converter.getPrivateKey(privateKeyInfo)
            }
            is PrivateKeyInfo -> {
                // PKCS8 unencrypted format
                converter.getPrivateKey(obj)
            }
            else -> throw IllegalArgumentException("Unsupported key format in $keyPath Found: ${obj?.javaClass?.simpleName}" )
        }
    }
}
