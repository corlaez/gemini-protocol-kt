package com.corlaez.gemini

import com.corlaez.server.createSSLContextFromKeyStore
import com.corlaez.server.letsEncryptCertificate
import com.corlaez.server.selfSignedCertificate
import java.security.KeyStore
import javax.net.ssl.SSLContext

/** Certificate configuration for TLS */
public sealed class CertificateConfig {
    public abstract val hostname: String
    public abstract fun createKeyStore(): KeyStore

    internal fun createSSLContext(): SSLContext {
        return createSSLContextFromKeyStore(createKeyStore())
    }

    public class LetsEncrypt(
        public override val hostname: String,
        public val keyPath: String,
        public val fullchainPath: String
    ) : CertificateConfig() {
        public companion object {
            public fun fromDomain(domain: String): LetsEncrypt {
                return LetsEncrypt(
                    hostname = domain,
                    keyPath = "/etc/letsencrypt/live/$domain/privkey.pem",
                    fullchainPath = "/etc/letsencrypt/live/$domain/fullchain.pem"
                )
            }
        }

        override fun createKeyStore(): KeyStore {
            return letsEncryptCertificate(keyPath, fullchainPath)
        }
    }

    public class SelfSigned(
        override val hostname: String = "localhost"
    ) : CertificateConfig() {
        override fun createKeyStore(): KeyStore {
            return selfSignedCertificate(hostname)
        }
    }
}