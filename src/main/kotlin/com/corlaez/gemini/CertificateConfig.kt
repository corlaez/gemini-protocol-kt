package com.corlaez.gemini

import com.corlaez.server.createSSLContextFromKeyStore
import com.corlaez.server.generatedSelfSignedCertificate
import com.corlaez.server.loadKeyStoreFromFiles
import java.security.KeyStore
import javax.net.ssl.SSLContext

/** Certificate configuration for TLS */
public sealed class CertificateConfig {
    public abstract fun createKeyStore(): KeyStore

    internal fun createSSLContext(): SSLContext {
        return createSSLContextFromKeyStore(createKeyStore())
    }

    public class LetsEncrypt(
        public val privateKeyPath: String,
        public val fullchainPath: String,
    ) : CertificateConfig() {
        public companion object {
            public fun fromDomain(domain: String): LetsEncrypt {
                return LetsEncrypt(
                    fullchainPath = "/etc/letsencrypt/live/$domain/fullchain.pem",
                    privateKeyPath = "/etc/letsencrypt/live/$domain/privkey.pem",
                )
            }
        }

        override fun createKeyStore(): KeyStore {
            return loadKeyStoreFromFiles(privateKeyPath, fullchainPath, CharArray(0))
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
        public val privateKeyPassword: CharArray = CharArray(0),
    ) : CertificateConfig() {
        override fun createKeyStore(): KeyStore {
            return loadKeyStoreFromFiles(privateKeyPath, certPemPath, privateKeyPassword)
        }
    }
}
