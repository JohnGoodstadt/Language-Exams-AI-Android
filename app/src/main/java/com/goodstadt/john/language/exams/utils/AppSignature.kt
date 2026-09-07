package com.goodstadt.john.language.exams.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import timber.log.Timber
import java.security.MessageDigest

/**
 * Reads the running app's own signing‑certificate SHA‑1, for the `X-Android-Cert` header that Google Cloud
 * uses when an API key is restricted to "Android apps". Computed at runtime so it matches whichever cert
 * actually signed this build (debug or release) - no need to hard‑code fingerprints in the app.
 *
 * The value is colon‑separated uppercase hex (e.g. "DA:39:A3:EE:..."), matching how the Cloud Console
 * shows the SHA‑1, so you register the exact same string there.
 */
object AppSignature {

    /** SHA‑1 fingerprint of this build's signing certificate, or null if it can't be read. */
    fun certSha1(context: Context): String? = try {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            ).signatures
        }
        val cert = signatures?.firstOrNull()
        if (cert == null) null else {
            val digest = MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        }
    } catch (e: Exception) {
        Timber.e(e, "AppSignature.certSha1 failed")
        null
    }

    /**
     * SHA‑1 for the `X-Android-Cert` HTTP header: the SAME fingerprint as [certSha1] but with the colons
     * stripped (bare hex). Google's servers reject the colon‑separated form in the header even though the
     * Cloud Console stores/displays the fingerprint with colons.
     */
    fun certSha1NoColons(context: Context): String? = certSha1(context)?.replace(":", "")
}
