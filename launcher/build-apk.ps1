param(
    [string]$KeyStore = $env:UNITV_LAUNCHER_KEY,
    [string]$SdkPath = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
if (-not $KeyStore) { $KeyStore = Join-Path $projectDir 'signing\debug.keystore' }
if (-not (Test-Path -LiteralPath $KeyStore)) { throw 'Informe -KeyStore com o caminho da sua debug.keystore original.' }
$env:UNITV_LAUNCHER_KEY = (Resolve-Path -LiteralPath $KeyStore).Path
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $candidate) { $SdkPath = $candidate }
}
if ($SdkPath) { $env:ANDROID_HOME = (Resolve-Path -LiteralPath $SdkPath).Path }
$certificateFile = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString() + '.der')
try {
    & keytool -exportcert -keystore $env:UNITV_LAUNCHER_KEY -storepass android -alias androiddebugkey -file $certificateFile
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel ler o certificado da chave debug original.' }
    $actual = (Get-FileHash -LiteralPath $certificateFile -Algorithm SHA256).Hash.ToLowerInvariant()
    $expected = (Get-Content -LiteralPath (Join-Path $projectDir 'signing\expected-certificate.sha256') -Raw).Trim()
    if ($actual -ne $expected) { throw 'Certificado diferente do launcher/ponte UniTV. Use a chave original.' }
} finally { if (Test-Path -LiteralPath $certificateFile) { Remove-Item -LiteralPath $certificateFile } }
Push-Location $projectDir
try {
    & .\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao ou os testes falharam. Consulte a mensagem do Gradle.' }
    $dist = Join-Path $projectDir 'dist'
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Copy-Item -LiteralPath '.\app\build\outputs\apk\release\app-release.apk' -Destination (Join-Path $dist 'CleanYourTV.apk') -Force
    Write-Host ('APK: ' + (Join-Path $dist 'CleanYourTV.apk'))
} finally { Pop-Location }
