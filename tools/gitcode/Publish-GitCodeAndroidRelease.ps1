[CmdletBinding()]
param(
    [string]$ReleaseNotesZhCn,
    [string]$ReleaseNotesEnUs,
    [string]$Token,
    [switch]$PrepareOnly,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$remotes = @(& git -C $repoRoot remote 2>$null)
if ($remotes -contains "gitcode") { throw "Refusing to run while the Android source repository has a gitcode remote." }
if ([string]::IsNullOrWhiteSpace($ReleaseNotesZhCn)) { throw "Stable releases require Chinese release notes." }

$properties = @{}
Get-Content -LiteralPath (Join-Path $repoRoot "version.properties") -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $properties[$matches[1]] = $matches[2] }
}
$versionCode = [int]$properties.VERSION_CODE
$versionName = [string]$properties.VERSION_NAME
if ($versionCode -le 0 -or [string]::IsNullOrWhiteSpace($versionName)) { throw "version.properties is invalid." }
if ($versionName.Length -gt 64 -or $versionName -notmatch '^[A-Za-z0-9._-]+$') {
    throw "VERSION_NAME is not safe for Android release naming. Use 1-64 ASCII letters, digits, dots, underscores, or hyphens."
}
$releaseTag = "android-v$versionName-c$versionCode"
$assetName = "MyCodex-Android-v$versionName-c$versionCode.apk"
if ($assetName.Length -gt 128) { throw "The Android release APK file name exceeds the client limit." }
$artifactDir = Join-Path $repoRoot "artifacts\update"
$versionedApk = Join-Path $artifactDir $assetName
$manifestPath = Join-Path $artifactDir "android-stable.json"
$apiBase = "https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates"
$packageBaseUrl = "$apiBase/releases/$releaseTag/attach_files/"
$publicApk = "$packageBaseUrl$assetName/download"
[IO.Directory]::CreateDirectory($artifactDir) | Out-Null

function Test-PublicTag([string]$Tag) {
    $text = (& git ls-remote --tags https://gitcode.com/gcw_SpGZ48lW/mycodex-updates.git "refs/tags/$Tag" | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Unable to query the GitCode release tag." }
    return -not [string]::IsNullOrWhiteSpace($text)
}
function Confirm-PublicAsset([string]$Url, [string]$ExpectedPath) {
    $temporary = [IO.Path]::GetTempFileName()
    try {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $temporary -TimeoutSec 900 -UseBasicParsing
        } catch {
            throw "Unable to download the public Android release asset."
        }
        if ((Get-Item $temporary).Length -ne (Get-Item $ExpectedPath).Length) {
            throw "Published APK differs from the candidate; increase versionCode."
        }
        if ((Get-FileHash $temporary -Algorithm SHA256).Hash -ne (Get-FileHash $ExpectedPath -Algorithm SHA256).Hash) {
            throw "Published APK differs from the candidate; increase versionCode."
        }
    } finally {
        Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    }
}

if ($PrepareOnly) {
    if (-not $SkipBuild) {
        & (Join-Path $repoRoot "gradlew.bat") -p $repoRoot assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Android stable release build failed." }
    }
    $builtApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path -LiteralPath $builtApk)) { throw "Release APK was not found: $builtApk" }
    Copy-Item -LiteralPath $builtApk -Destination $versionedApk -Force
} else {
    $worktreeStatus = (& git -C $repoRoot status --porcelain | Out-String).Trim()
    if (-not [string]::IsNullOrWhiteSpace($worktreeStatus)) { throw "Live publication requires a clean working tree." }
    if ([string]::IsNullOrWhiteSpace($Token)) { $Token = $env:GITCODE_TOKEN }
    if ([string]::IsNullOrWhiteSpace($Token)) { throw "GitCode token is required for live publication." }
    if (-not $SkipBuild) {
        & (Join-Path $repoRoot "gradlew.bat") -p $repoRoot assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "Android stable release build failed." }
    }
    $builtApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path -LiteralPath $builtApk)) { throw "Release APK was not found: $builtApk" }
    Copy-Item -LiteralPath $builtApk -Destination $versionedApk -Force
}

& (Join-Path $repoRoot "tools\update\New-MyCodexAndroidUpdateManifest.ps1") `
    -ApkPath $versionedApk -PackageBaseUrl $packageBaseUrl `
    -ReleaseNotesZhCn $ReleaseNotesZhCn -ReleaseNotesEnUs $ReleaseNotesEnUs -OutputPath $manifestPath
if ($LASTEXITCODE -ne 0) { throw "Manifest generation failed." }

if ($PrepareOnly) {
    Write-Host "Prepared stable-channel APK without publishing:"
    Write-Host "  Pointer channel: stable"
    Write-Host "  APK: $versionedApk"
    Write-Host "  Manifest: $manifestPath"
    return
}

function Invoke-GitCodeApi([string]$Method, [string]$Path, [hashtable]$Body) {
    $request = @{} + $Body
    $request.access_token = $Token
    try {
        Invoke-RestMethod -Method $Method -Uri "$apiBase/$Path" -Body $request -TimeoutSec 120
    } catch {
        throw "GitCode release operation failed."
    }
}
function New-Release([string]$Tag, [string]$Name, [string]$Body) {
    Invoke-GitCodeApi "Post" "releases" @{ tag_name = $Tag; name = $Name; body = $Body; target_commitish = "main" } | Out-Null
}
function Send-Asset([string]$Tag, [string]$Path) {
    $name = Split-Path -Leaf $Path
    $tokenEncoded = [Uri]::EscapeDataString($Token)
    $nameEncoded = [Uri]::EscapeDataString($name)
    try {
        $upload = Invoke-RestMethod -Uri "$apiBase/releases/$Tag/upload_url?access_token=$tokenEncoded&file_name=$nameEncoded" -TimeoutSec 120
    } catch {
        throw "Unable to request GitCode asset upload authorization."
    }
    $headers = @{}
    $upload.headers.PSObject.Properties | ForEach-Object { $headers[$_.Name] = [string]$_.Value }
    try {
        Invoke-WebRequest -Method Put -Uri $upload.url -Headers $headers -InFile $Path -ContentType "application/octet-stream" -TimeoutSec 900 -UseBasicParsing | Out-Null
    } catch {
        throw "Unable to upload the GitCode release asset."
    }
}
function Remove-Pointer([string]$Tag) {
    if (-not (Test-PublicTag $Tag)) { return }
    $askPass = Join-Path $env:TEMP "mycodex-gitcode-askpass-$PID.cmd"
    try {
        [IO.File]::WriteAllText($askPass, "@echo off`r`nif /I `%1==Username (echo gcw_SpGZ48lW) else (echo %GITCODE_TOKEN%)`r`n", [Text.Encoding]::ASCII)
        $oldAsk = $env:GIT_ASKPASS; $oldPrompt = $env:GIT_TERMINAL_PROMPT; $oldToken = $env:GITCODE_TOKEN
        $env:GIT_ASKPASS = $askPass; $env:GIT_TERMINAL_PROMPT = "0"; $env:GITCODE_TOKEN = $Token
        & git -c credential.helper= push https://gitcode.com/gcw_SpGZ48lW/mycodex-updates.git ":refs/tags/$Tag"
        if ($LASTEXITCODE -ne 0) { throw "Unable to replace the Android update pointer." }
    } finally {
        $env:GIT_ASKPASS = $oldAsk; $env:GIT_TERMINAL_PROMPT = $oldPrompt; $env:GITCODE_TOKEN = $oldToken
        Remove-Item -LiteralPath $askPass -Force -ErrorAction SilentlyContinue
    }
}

$releaseExists = Test-PublicTag $releaseTag
if ($releaseExists) {
    Confirm-PublicAsset $publicApk $versionedApk
} else {
    New-Release $releaseTag "MyCodex Android $versionName" (($ReleaseNotesZhCn + "`n`n" + $ReleaseNotesEnUs).Trim())
    Send-Asset $releaseTag $versionedApk
    Confirm-PublicAsset $publicApk $versionedApk
}

$pointerTag = "updates-android-stable"
Remove-Pointer $pointerTag
New-Release $pointerTag "Android stable update manifest" "MyCodex Android stable update pointer."
Send-Asset $pointerTag $manifestPath
$manifestUrl = "$apiBase/releases/$pointerTag/attach_files/android-stable.json/download"
try {
    $published = Invoke-RestMethod -Uri $manifestUrl -TimeoutSec 120
} catch {
    throw "Unable to verify the published Android update manifest."
}
if ([long]$published.app.versionCode -ne $versionCode -or $published.channel -ne "stable") { throw "Published manifest verification failed." }
Confirm-PublicAsset $publicApk $versionedApk
Write-Host "Published $releaseTag; stable pointer updated last: $manifestUrl"
