# 서명한 출시용 AAB 만들기 (Google Play 업로드용)
#
#   powershell -ExecutionPolicy Bypass -File tools\build-release.ps1
#
# 업로드 키 비밀번호를 가려서 묻고, 이 창의 환경 변수로만 넘긴다 (파일에 저장하지 않음).
# 결과: dist\TVReaderForKomga-v<버전>-b<빌드>.aab
param(
    [string]$KeyStore = "D:\Keys\komgatv-upload.jks",
    [string]$KeyAlias = "upload"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path $KeyStore)) { throw "키 파일이 없습니다: $KeyStore" }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" }

$secure = Read-Host "업로드 키 비밀번호" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:KOMGATV_UPLOAD_STORE_FILE = $KeyStore
    $env:KOMGATV_UPLOAD_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $env:KOMGATV_UPLOAD_KEY_ALIAS = $KeyAlias
    & .\gradlew.bat bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "빌드 실패 (exit $LASTEXITCODE)" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    Remove-Item Env:KOMGATV_UPLOAD_STORE_PASSWORD -ErrorAction SilentlyContinue
}

$aab = Get-ChildItem "app\build\outputs\bundle\release\*.aab" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
New-Item -ItemType Directory -Force "dist" | Out-Null
Copy-Item $aab.FullName "dist\" -Force
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify "dist\$($aab.Name)" | Select-Object -Last 1
Write-Host "완료: $root\dist\$($aab.Name)"
explorer.exe "$root\dist"
