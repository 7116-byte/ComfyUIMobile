[CmdletBinding()]
param(
    [ValidateSet('check', 'test', 'build')][string]$Task = 'check',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\Enter-DevEnvironment.ps1')
if ($Task -ne 'build') {
    foreach ($test in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'scripts') -Filter 'test-*.cjs' -File | Sort-Object Name) {
        & node $test.FullName
        if ($LASTEXITCODE -ne 0) { throw "Regression failed: $($test.Name)" }
    }
}
$gradleArguments = @('--no-daemon', '--console=plain', '--max-workers=2', '-I', (Join-Path $PSScriptRoot 'scripts\offline-tests.init.gradle'))
if (!$Online) { $gradleArguments += '--offline' }
if ($Task -ne 'build') { $gradleArguments += 'testDebugUnitTest' }
if ($Task -ne 'test') { $gradleArguments += @('assembleRelease', 'lintVitalRelease') }
& (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
if ($Task -ne 'test') {
    $buildConfig = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'app\build.gradle.kts') -Raw
    $version = [regex]::Match($buildConfig, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $versionCode = [regex]::Match($buildConfig, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    if (!$version -or !$versionCode) { throw 'Cannot determine APK version.' }
    $apkName = "ComfyUIMobile-v$version-release.apk"
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\$apkName"
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'
    $verification = & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certificate = 'bc6af4bd77ea5fe76c2f5b6b7b9484f53dd678b47879907180bf27a36c17a4df'
    if (!($verification | Select-String -SimpleMatch "Signer #1 certificate SHA-256 digest: $certificate")) {
        throw 'APK signing certificate does not match the original release.'
    }
    $badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk
    if ($LASTEXITCODE -ne 0 -or !($badging | Select-String -SimpleMatch "package: name='com.local.comfyuimobile' versionCode='$versionCode' versionName='$version'")) {
        throw 'APK package or version verification failed.'
    }
    $digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$apk.sha256", "$digest  $apkName`n", [Text.UTF8Encoding]::new($false))
    Write-Host "Verified original release signature, package and version: $apk"
    Write-Host "SHA-256: $digest"
}
