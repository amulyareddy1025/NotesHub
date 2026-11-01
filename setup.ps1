# One-time setup for Windows (called by setup.bat)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$jar = "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $jar)) {
    Write-Host "Downloading gradle-wrapper.jar ..."
    Invoke-WebRequest -UseBasicParsing `
        -Uri "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar" `
        -OutFile $jar
}

if (-not (Test-Path "local.properties")) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if ($sdk) {
        $sdk = $sdk -replace '\\', '/'
        "sdk.dir=$sdk" | Out-File -Encoding ascii local.properties
        Write-Host "Created local.properties (sdk.dir=$sdk)"
    } else {
        Write-Host "WARNING: ANDROID_HOME is not set. Set it, or create local.properties with sdk.dir=<path>"
    }
}
Write-Host "Setup complete. Now run: gradlew.bat assembleDebug"
