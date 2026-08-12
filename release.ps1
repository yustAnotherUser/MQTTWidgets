# release.ps1 - bump, build, deploy, commit, release (MQTT Widgets)
#
# One-shot pipeline used by both humans and agent sessions:
#   1. Bump versionName + versionCode (app/build.gradle.kts) and README badge
#   2. Clean rebuild (gradlew clean :app:assembleDebug) on JDK 17
#   3. Deploy APK + index.html to the local download webserver
#   4. Commit + push source to GitHub
#   5. Create a GitHub Release (tag v<version>) and attach the APK
#
# Usage:
#   .\release.ps1 -Version 2.9.3 -Notes "..." -CommitMessage "..."
#   .\release.ps1                                  # auto-increment patch version
#   .\release.ps1 -SkipBuild -SkipWeb -SkipGit     # only create release
#
# Agent usage (detached so the build keeps running, then poll the log):
#   Start-Process powershell -Args '-ExecutionPolicy','Bypass','-File','release.ps1',`
#       '-Version','2.9.3','-CommitMessage','...','-Notes','...' `
#       -WorkingDirectory "C:\Users\micro\Desktop\android-app-mqttclient-widgets" -PassThru
#   Poll $LogPath for BUILD_OK / WEB_OK / PUSH_OK / RELEASE_OK or errors.

param(
    [string]$Version = "",
    [string]$CommitMessage = "",
    [string]$Notes = "",
    [switch]$SkipBuild,
    [switch]$SkipWeb,
    [switch]$SkipGit,
    [switch]$SkipRelease
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# --- machine-specific settings ---
$JdkPath   = "C:\Users\micro\jdk-17\jdk-17.0.12"          # JDK 17 required by Gradle 8.11.1
$WebRoot   = "C:\Users\micro\mqtt-widgets-download"        # python -m http.server 8888 directory
$GitRepo   = "yustAnotherUser/MQTTWidgets"
$LogPath   = "C:\Users\micro\AppData\Local\Temp\opencode\mqttwidgets-release.log"  # stable path for agent polling

function Log([string]$m) {
    $line = "[{0:HH:mm:ss}] {1}" -f (Get-Date), $m
    Write-Host $line
    Add-Content -LiteralPath $LogPath -Value $line -Encoding UTF8
}
function WriteUtf8([string]$path, [string]$content) {
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

Log "======== release.ps1 start ========"

# ---------- 1. version bump ----------
$kts   = Join-Path $Root "app\build.gradle.kts"
$readme = Join-Path $Root "README.md"
$apkSrc = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
$idx   = Join-Path $WebRoot "index.html"

$ktsContent   = [System.IO.File]::ReadAllText($kts)
$curCode      = [int][regex]::Match($ktsContent, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$curVersion   = [regex]::Match($ktsContent, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $Version) {
    # auto-increment patch, e.g. 2.9.2 -> 2.9.3
    $parts = $curVersion.Split('.')
    $Version = ($parts[0] + "." + $parts[1] + "." + ([int]$parts[2] + 1))
    Log "Auto-increment: $curVersion -> $Version"
}
$newCode = $curCode + 1
Log "New versionName=$Version, versionCode=$newCode"

$ktsContent = $ktsContent -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode" `
                          -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$Version`""
WriteUtf8 $kts $ktsContent

$readmeContent = [System.IO.File]::ReadAllText($readme)
$readmeContent = $readmeContent -replace 'version-\d+\.\d+\.\d+-blue', "version-$Version-blue"
WriteUtf8 $readme $readmeContent
Log "Bump done"

# ---------- 2. clean build (JDK 17) ----------
if (-not $SkipBuild) {
    Log "Building with JAVA_HOME=$JdkPath ... (tail $LogPath for progress)"
    Push-Location $Root
    try {
        if (-not (Test-Path (Join-Path $JdkPath "bin\java.exe"))) {
            throw "JDK 17 not found at $JdkPath"
        }
        $env:JAVA_HOME = $JdkPath
        & cmd.exe /c "gradlew.bat clean :app:assembleDebug --console=plain" *>> $LogPath
        $code = $LASTEXITCODE
        if ($code -ne 0) { throw "gradle build failed with exit code $code" }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $apkSrc)) { throw "APK missing after build: $apkSrc" }
    Log "BUILD_OK -> $apkSrc"
} else {
    Log "SKIP build"
}

# ---------- 3. deploy to local webserver ----------
if (-not $SkipWeb) {
    if (-not (Test-Path $apkSrc)) { throw "No APK at $apkSrc - run without -SkipBuild first" }
    $webApk = Join-Path $WebRoot "MQTTWidgets-v$Version-debug.apk"
    Copy-Item -LiteralPath $apkSrc -Destination $webApk -Force
    if (Test-Path $idx) {
        $html = [System.IO.File]::ReadAllText($idx)
        $html = $html -replace 'v\d+\.\d+\.\d+', "v$Version" `
                      -replace 'MQTTWidgets-v\d+\.\d+\.\d+-debug\.apk', "MQTTWidgets-v$Version-debug.apk"
        WriteUtf8 $idx $html
    } else {
        Log "WARN: index.html not found in $WebRoot - skipped"
    }
    Log "WEB_OK -> $webApk (http://192.168.178.39:8888/)"
} else {
    Log "SKIP web deploy"
}

# ---------- 4. git commit + push ----------
if (-not $SkipGit) {
    Push-Location $Root
    try {
        git add -A
        git commit -m $(if ($CommitMessage) { $CommitMessage } else { "Release v$Version" }) | Out-Null
        git push origin main
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { throw "git push failed with exit code $LASTEXITCODE" }
    Log "PUSH_OK ($Root)"
} else {
    Log "SKIP git push"
}

# ---------- 5. GitHub release ----------
if (-not $SkipRelease) {
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { Log "GH_AUTH_MISSING - run 'gh auth login'; skipping release"; exit 1 }
    $webApk = Join-Path $WebRoot "MQTTWidgets-v$Version-debug.apk"
    if (-not (Test-Path $webApk)) { throw "Release APK missing: $webApk - run without -SkipWeb first" }
    $body = if ($Notes) { $Notes } else { "Debug build $Version" }
    gh release create "v$Version" $webApk `
        --repo $GitRepo `
        --title "MQTT Widgets v$Version" `
        --notes $body
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
    Log "RELEASE_OK https://github.com/$GitRepo/releases/tag/v$Version"
} else {
    Log "SKIP release"
}

Log "======== release.ps1 done ========"