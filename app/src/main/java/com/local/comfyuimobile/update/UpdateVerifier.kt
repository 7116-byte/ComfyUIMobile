package com.local.comfyuimobile.update

object UpdateVerifier {
    fun verifyMetadata(
        expectedPackage: String,
        actualPackage: String,
        installedVersionCode: Long,
        archiveVersionCode: Long,
        installedCertificate: ByteArray,
        archiveCertificate: ByteArray,
    ) {
        require(actualPackage == expectedPackage) { "APK 包名不匹配" }
        require(archiveVersionCode > installedVersionCode) { "更新版本没有高于当前版本" }
        require(archiveCertificate.contentEquals(installedCertificate)) { "APK 签名与当前应用不一致" }
    }
}
