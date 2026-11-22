param(
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [ValidateRange(1,2100000000)][int]$VersionCode = 1,
    [string]$VersionName = '1.0',
    [string]$SigningKey
)
$ErrorActionPreference = 'Stop'
$AndroidJar = (Resolve-Path -LiteralPath $AndroidJar).Path
$BuildTools = (Resolve-Path -LiteralPath $BuildTools).Path
$build = Join-Path ([IO.Path]::GetTempPath()) ('chumen-build-' + [guid]::NewGuid())
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$generated = Join-Path $build 'generated'
New-Item -ItemType Directory $classes,$dex,$generated | Out-Null

function Run([string]$exe, [array]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe exited with $LASTEXITCODE" }
}
function Get-LocalSigningKey {
    $keyDir = Join-Path $env:LOCALAPPDATA 'ChumenSigning'
    New-Item -ItemType Directory -Force $keyDir | Out-Null
    $key = Join-Path $keyDir 'family.p12'
    $secretFile = Join-Path $keyDir 'credential.dpapi'
    if (!(Test-Path -LiteralPath $secretFile)) {
        if (Test-Path -LiteralPath $key) { throw 'Restore the original signing credential before rebuilding.' }
        $secret = ConvertTo-SecureString ([guid]::NewGuid().ToString('N')) -AsPlainText -Force
        $secret | ConvertFrom-SecureString | Set-Content -LiteralPath $secretFile
    }
    $secret = Get-Content -Raw -LiteralPath $secretFile
    $cred = New-Object PSCredential('signer',(ConvertTo-SecureString $secret.Trim()))
    $env:CHUMEN_SIGN_PASS = $cred.GetNetworkCredential().Password
    if (!(Test-Path -LiteralPath $key)) {
        Run 'keytool' @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-alias','family','-keyalg','RSA','-keysize','3072','-validity','10000','-storepass:env','CHUMEN_SIGN_PASS','-dname','CN=Chumen Family')
    }
    return $key
}

Push-Location $PSScriptRoot
try {
    Run "$BuildTools/aapt2.exe" @('compile','--dir','res','-o',"$build/res.zip")
    Run "$BuildTools/aapt2.exe" @('link','-I',$AndroidJar,'--manifest','AndroidManifest.xml','--version-code',"$VersionCode",'--version-name',$VersionName,'--java',$generated,'-o',"$build/base.apk","$build/res.zip")
    $sources = (Get-ChildItem 'src',$generated -Recurse -Filter '*.java').FullName
    Run 'javac' (@('-encoding','UTF-8','--release','8','-classpath',$AndroidJar,'-d',$classes) + $sources)
    Run 'jar' @('cf',"$build/classes.jar",'-C',$classes,'.')
    Run "$BuildTools/d8.bat" @('--release','--min-api','26','--lib',$AndroidJar,'--output',$dex,"$build/classes.jar")
    Run 'jar' @('uf',"$build/base.apk",'-C',$dex,'classes.dex')
    Run "$BuildTools/zipalign.exe" @('-f','4',"$build/base.apk","$build/aligned.apk")

    if ($SigningKey) {
        $key = (Resolve-Path -LiteralPath $SigningKey).Path
        if (!$env:CHUMEN_SIGN_PASS) { throw 'Missing signing password.' }
    } else {
        $key = Get-LocalSigningKey
    }
    Run "$BuildTools/apksigner.bat" @('sign','--ks',$key,'--ks-pass','env:CHUMEN_SIGN_PASS','--v4-signing-enabled','false','--out',"$build/Chumen.apk","$build/aligned.apk")
    Run "$BuildTools/apksigner.bat" @('verify','--verbose',"$build/Chumen.apk")
    Copy-Item -LiteralPath "$build/Chumen.apk" -Destination 'Chumen.apk' -Force
} finally {
    Remove-Item Env:CHUMEN_SIGN_PASS -ErrorAction SilentlyContinue
    Pop-Location
}
