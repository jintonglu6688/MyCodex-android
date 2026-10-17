[CmdletBinding()]
param(
    [int]$VersionCode,
    [string]$VersionName,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$PackageBaseUrl,
    [string]$ReleaseNotesZhCn,
    [string]$ReleaseNotesEnUs,
    [string]$OutputPath,
    [string]$AssetUrlSuffix = "/download"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$versionFile = Join-Path $repoRoot "version.properties"
if (-not (Test-Path -LiteralPath $versionFile)) { throw "version.properties was not found." }
$properties = @{}
Get-Content -LiteralPath $versionFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $properties[$matches[1]] = $matches[2] }
}
$sourceCode = 0
if (-not [int]::TryParse($properties.VERSION_CODE, [ref]$sourceCode) -or $sourceCode -le 0) { throw "VERSION_CODE is invalid." }
$sourceName = [string]$properties.VERSION_NAME
if ([string]::IsNullOrWhiteSpace($sourceName)) { throw "VERSION_NAME is invalid." }
if ($VersionCode -eq 0) { $VersionCode = $sourceCode }
if ([string]::IsNullOrWhiteSpace($VersionName)) { $VersionName = $sourceName }
if ($VersionCode -ne $sourceCode -or $VersionName -ne $sourceName) { throw "Requested version does not match version.properties." }
if ($VersionName.Length -gt 64 -or $VersionName -notmatch '^[A-Za-z0-9._-]+$') {
    throw "VERSION_NAME is not safe for Android release naming. Use 1-64 ASCII letters, digits, dots, underscores, or hyphens."
}
if ([string]::IsNullOrWhiteSpace($ReleaseNotesZhCn)) { throw "Stable releases require Chinese release notes." }

$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$expectedName = "MyCodex-Android-v$VersionName-c$VersionCode.apk"
if ($expectedName.Length -gt 128) { throw "The Android release APK file name exceeds the client limit." }
if ((Split-Path -Leaf $apk) -ne $expectedName) { throw "APK file name must be $expectedName." }
if ($PackageBaseUrl -notmatch '^https://api\.gitcode\.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v[^/]+-c[1-9][0-9]*/attach_files/$') {
    throw "PackageBaseUrl is not an allowed GitCode release attachment URL."
}

$sdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $localProperties = Join-Path $repoRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\\\', '\' }
    }
}
if (-not (Test-Path -LiteralPath $sdkRoot)) { throw "Android SDK was not found." }
$apkanalyzer = Get-ChildItem -Path (Join-Path $sdkRoot "cmdline-tools") -Recurse -Filter "apkanalyzer.bat" | Select-Object -First 1 -ExpandProperty FullName
$apksigner = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Directory | Sort-Object Name -Descending | ForEach-Object {
    Join-Path $_.FullName "apksigner.bat"
} | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $apkanalyzer -or -not $apksigner) { throw "apkanalyzer or apksigner was not found in the Android SDK." }

function Invoke-ApkAnalyzer([string]$Field) {
    $value = (& $apkanalyzer manifest $Field $apk 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { throw "Unable to read APK $Field." }
    return $value
}

$apkApplicationId = Invoke-ApkAnalyzer "application-id"
$apkVersionCode = [long](Invoke-ApkAnalyzer "version-code")
$apkVersionName = Invoke-ApkAnalyzer "version-name"
$apkMinSdk = [int](Invoke-ApkAnalyzer "min-sdk")
if ($apkApplicationId -ne "com.mycodex.mobile") { throw "APK application ID mismatch." }
if ($apkVersionCode -ne $VersionCode -or $apkVersionName -ne $VersionName) { throw "APK version does not match version.properties." }
$stableManifestUrl = "https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/updates-android-stable/attach_files/android-stable.json/download"
$apkManifestUrl = (& $apkanalyzer resources value --config default --name update_manifest_url --type string $apk 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $apkManifestUrl -ne $stableManifestUrl) { throw "APK stable manifest URL is invalid." }
$signatureOutput = (& $apksigner verify --print-certs $apk 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed." }
$certificateSha256 = [regex]::Match($signatureOutput, '(?im)Signer #1 certificate SHA-256 digest:\s*([0-9a-f]+)').Groups[1].Value
if ($certificateSha256.Length -ne 64) { throw "Unable to read the APK signing certificate SHA-256 fingerprint." }

$size = (Get-Item -LiteralPath $apk).Length
$sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    schemaVersion = "1"
    channel = "stable"
    generatedAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    packageBaseUrl = $PackageBaseUrl
    app = [ordered]@{
        applicationId = $apkApplicationId
        versionCode = $apkVersionCode
        versionName = $apkVersionName
        minimumAndroidApi = $apkMinSdk
        releaseDate = [DateTime]::UtcNow.ToString("yyyy-MM-dd")
        releaseNotes = [ordered]@{ "zh-CN" = [string]$ReleaseNotesZhCn; "en-US" = [string]$ReleaseNotesEnUs }
        asset = [ordered]@{
            url = "$expectedName$AssetUrlSuffix"
            fileName = $expectedName
            sha256 = $sha256
            size = $size
        }
    }
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) { $OutputPath = Join-Path $repoRoot "artifacts\update\android-stable.json" }
$output = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory((Split-Path -Parent $output)) | Out-Null
$json = $manifest | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText($output, $json + "`n", [Text.UTF8Encoding]::new($false))
$roundTrip = Get-Content -Raw -LiteralPath $output -Encoding UTF8 | ConvertFrom-Json
if ($roundTrip.schemaVersion -ne "1" -or $roundTrip.channel -ne "stable" -or $roundTrip.app.asset.sha256 -ne $sha256) {
    throw "Generated manifest verification failed."
}
Write-Host "Android update manifest generated: $output"
Write-Host "APK: $expectedName ($size bytes)"
Write-Host "SHA-256: $sha256"
Write-Host "Signing certificate SHA-256: $certificateSha256"
