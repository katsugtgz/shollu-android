package com.ebsoft.shollu.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Digest + package/signature/versionCode guards for a downloaded APK. Digest is
 * JVM-safe; archive inspection uses PackageManager.
 */
object ApkVerifier {

    const val EXPECTED_PACKAGE = UpdatePolicy.PACKAGE_NAME

    fun digestMatches(file: File, digestHeader: String): Boolean {
        val expected = UpdatePolicy.parseSha256Digest(digestHeader) ?: return false
        return UpdatePolicy.digestEquals(expected, UpdatePolicy.sha256OfFile(file))
    }

    /**
     * Null = acceptable. Otherwise an Indonesian error for the prompt.
     */
    fun rejectReason(
        context: Context,
        apk: File,
        offer: UpdateOffer,
        installed: InstalledApp
    ): String? {
        if (!digestMatches(apk, offer.digest)) {
            return "Berkas pembaruan rusak (hash tidak cocok)."
        }
        val archive = packageArchiveInfo(context, apk) ?: return "Berkas APK tidak terbaca."
        val archiveName = archive.packageName
        if (archiveName != EXPECTED_PACKAGE || archiveName != context.packageName) {
            return "Paket pembaruan tidak cocok."
        }
        val archiveCode = archiveVersionCode(archive)
        val remoteCode = UpdatePolicy.versionCodeFromTag(offer.tagName)?.toLong()
        if (remoteCode == null || archiveCode <= installed.versionCode || archiveCode != remoteCode) {
            return "Kode versi pembaruan tidak valid."
        }
        val installedCerts = signingCerts(
            installedInfo(context, signing = true) ?: return "Aplikasi terpasang tidak terbaca."
        )
        val archiveCerts = signingCerts(archive)
        if (installedCerts.isEmpty() || archiveCerts.isEmpty() || installedCerts != archiveCerts) {
            return "Tanda tangan APK tidak cocok dengan yang terpasang."
        }
        return null
    }

    fun installedAppOf(context: Context): InstalledApp {
        val info = installedInfo(context, signing = false) ?: return InstalledApp(0L, "")
        return InstalledApp(archiveVersionCode(info), info.versionName ?: "")
    }

    private fun installedInfo(context: Context, signing: Boolean): PackageInfo? = try {
        val flags = if (signing) signingFlags() else 0
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun signingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun packageArchiveInfo(context: Context, apk: File): PackageInfo? {
        val path = apk.absolutePath
        val flags = signingFlags()
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(path, flags)
        } ?: return null
        info.applicationInfo?.sourceDir = path
        info.applicationInfo?.publicSourceDir = path
        return info
    }

    private fun archiveVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun signingCerts(info: PackageInfo): Set<String> {
        val bytes: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
        return bytes.map { cert ->
            cert.joinToString(separator = "") { b -> "%02x".format(b) }
        }.toSet()
    }
}
