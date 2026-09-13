param([Parameter(Mandatory=$true)][string]$SigningConfig,[Parameter(Mandatory=$true)][string]$Apk,[Parameter(Mandatory=$true)][string]$OutputApk)
$ErrorActionPreference='Stop'
if(-not $env:ANDROID_HOME){throw 'Set ANDROID_HOME to the Android SDK root'}
$rtaConfig=Get-Content -LiteralPath $SigningConfig -Raw | ConvertFrom-Json
$rtaKey=Join-Path (Split-Path (Resolve-Path -LiteralPath $SigningConfig).Path) $rtaConfig.keystore
if(-not (Test-Path -LiteralPath $rtaKey)){throw 'Persistent signing key is missing; do not generate a replacement for an existing V2 installation'}
$rtaBuildTools=Join-Path $env:ANDROID_HOME 'build-tools\34.0.0'
$rtaAligned=$OutputApk+'.aligned.tmp'
& (Join-Path $rtaBuildTools 'zipalign.exe') -f 4 $Apk $rtaAligned
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
try {
    $env:RTA_STORE_PASSWORD=$rtaConfig.password
    & (Join-Path $rtaBuildTools 'apksigner.bat') sign --ks $rtaKey --ks-key-alias $rtaConfig.alias --ks-pass env:RTA_STORE_PASSWORD --key-pass env:RTA_STORE_PASSWORD --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OutputApk $rtaAligned
    if($LASTEXITCODE -ne 0){throw 'Signing failed'}
} finally {Remove-Item Env:RTA_STORE_PASSWORD -ErrorAction SilentlyContinue}
& (Join-Path $rtaBuildTools 'apksigner.bat') verify --verbose --print-certs $OutputApk
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
& (Join-Path $rtaBuildTools 'zipalign.exe') -c 4 $OutputApk
if($LASTEXITCODE -ne 0){throw 'Alignment verification failed'}
Remove-Item -LiteralPath $rtaAligned
Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256
