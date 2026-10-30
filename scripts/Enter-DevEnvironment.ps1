$ErrorActionPreference = 'Stop'
$comfyProjectRoot = Split-Path -Parent $PSScriptRoot
$comfyLocalRoot = Join-Path $comfyProjectRoot '.local'
$env:JAVA_HOME = Join-Path $comfyLocalRoot 'android\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME = Join-Path $comfyLocalRoot 'android\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $comfyLocalRoot 'cache\gradle-home'
$env:COMFY_BACKUP_ROBOLECTRIC_DIR = Join-Path $comfyLocalRoot 'cache\robolectric'
$env:TEMP = Join-Path $comfyLocalRoot 'tmp'
$env:TMP = $env:TEMP
if (!(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Local JDK not found. See docs/development.md for the toolchain layout.'
}
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' +
    (Join-Path $comfyLocalRoot 'tools\node') + ';' +
    (Join-Path $comfyLocalRoot 'tools\Git\cmd') + ';' +
    (Join-Path $comfyLocalRoot 'tools\GitHubCLI') + ';' +
    (Join-Path $env:ANDROID_HOME 'platform-tools') + ';' + $env:PATH
Set-Location -LiteralPath $comfyProjectRoot
Write-Host "ComfyUI Mobile development environment: $comfyProjectRoot"
