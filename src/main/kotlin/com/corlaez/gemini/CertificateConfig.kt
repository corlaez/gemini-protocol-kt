package com.corlaez.gemini

import com.corlaez.server.createSSLContextFromKeyStore
import com.corlaez.server.fileSelfSignedCertificate
import com.corlaez.server.letsEncryptCertificate
import com.corlaez.server.generatedSelfSignedCertificate
import java.security.KeyStore
import javax.net.ssl.SSLContext

/** Certificate configuration for TLS */
public sealed class CertificateConfig {
    public abstract fun createKeyStore(): KeyStore

    internal fun createSSLContext(): SSLContext {
        return createSSLContextFromKeyStore(createKeyStore())
    }

    public class LetsEncrypt(
        public val keyPath: String,
        public val fullchainPath: String
    ) : CertificateConfig() {
        public companion object {
            public fun fromDomain(domain: String): LetsEncrypt {
                return LetsEncrypt(
                    keyPath = "/etc/letsencrypt/live/$domain/privkey.pem",
                    fullchainPath = "/etc/letsencrypt/live/$domain/fullchain.pem"
                )
            }
        }

        override fun createKeyStore(): KeyStore {
            return letsEncryptCertificate(keyPath, fullchainPath)
        }
    }

    public class GeneratedSelfSigned(
        public val hostname: String = "localhost"
    ) : CertificateConfig() {
        override fun createKeyStore(): KeyStore {
            return generatedSelfSignedCertificate(hostname)
        }
    }
    /**
     * Certificate configuration that loads self-signed certificates from a keystore file.
     * Supports JKS, PKCS12, and other keystore formats.
     */
    public class FileSelfSigned(
        public val privateKeyPath: String,
        public val certPemPath: String,
        public val privateKeyPassword: CharArray,
    ) : CertificateConfig() {
        override fun createKeyStore(): KeyStore {
            return fileSelfSignedCertificate(certPemPath, privateKeyPath, privateKeyPassword)
        }
    }
}
