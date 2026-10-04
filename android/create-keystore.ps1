# Creates the Arkhime release signing key and wires it into local.properties.
# Run from anywhere:  powershell -ExecutionPolicy Bypass -File android\create-keystore.ps1

$ErrorActionPreference = "Stop"

$androidDir = $PSScriptRoot
$keystoreName = "arkhime-release.jks"
$keystorePath = Join-Path $androidDir "app\$keystoreName"
$propsPath = Join-Path $androidDir "local.properties"

function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME "bin\keytool.exe" }
    $candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "keytool not found. Install a JDK or Android Studio."
}

function Read-Required([string]$prompt, [string]$default = "") {
    while ($true) {
        $label = if ($default) { "$prompt [$default]" } else { $prompt }
        $value = (Read-Host $label).Trim()
        if (-not $value) { $value = $default }
        if ($value) { return $value }
        Write-Host "  This one is required." -ForegroundColor Yellow
    }
}

function Read-Password {
    while ($true) {
        $first = [System.Net.NetworkCredential]::new("", (Read-Host "Keystore password (min 6 characters)" -AsSecureString)).Password
        if ($first.Length -lt 6) { Write-Host "  Too short." -ForegroundColor Yellow; continue }
        $second = [System.Net.NetworkCredential]::new("", (Read-Host "Repeat password" -AsSecureString)).Password
        if ($first -ne $second) { Write-Host "  Passwords don't match." -ForegroundColor Yellow; continue }
        return $first
    }
}

# commas and backslashes are special inside a keytool -dname
function Escape-Dn([string]$v) { return $v.Replace("\", "\\").Replace(",", "\,") }
# backslashes are escapes in .properties files
function Escape-Prop([string]$v) { return $v.Replace("\", "\\") }

if (Test-Path $keystorePath) {
    Write-Host "$keystorePath already exists." -ForegroundColor Red
    Write-Host "Refusing to overwrite it: a lost or replaced key means existing installs can never be updated."
    exit 1
}

$keytool = Find-Keytool
Write-Host "Using keytool: $keytool`n"
Write-Host "Creating the Arkhime release key. Your name/organisation are embedded in the certificate.`n" -ForegroundColor Cyan

$alias = Read-Required "Key alias" "arkhime"
$password = Read-Password
Write-Host ""
$name = Read-Required "Your name (or developer name)"
$orgUnit = Read-Required "Organisational unit" "Development"
$org = Read-Required "Organisation" "Arkhins"
$city = Read-Required "City"
$state = Read-Required "State / province"
do {
    $country = (Read-Required "Two-letter country code (e.g. IN, US)").ToUpper()
} until ($country -match '^[A-Z]{2}$')

$dname = "CN=$(Escape-Dn $name), OU=$(Escape-Dn $orgUnit), O=$(Escape-Dn $org), L=$(Escape-Dn $city), ST=$(Escape-Dn $state), C=$country"

# Pass the password through an env var so it never appears on a command line
$env:ARKHIME_KS_PASS = $password
try {
    # PKCS12 keystores use one password for both the store and the key
    & $keytool -genkeypair -v `
        -keystore $keystorePath -storetype PKCS12 `
        -alias $alias -keyalg RSA -keysize 4096 -validity 10000 `
        -dname $dname `
        -storepass:env ARKHIME_KS_PASS -keypass:env ARKHIME_KS_PASS
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

    # Update local.properties, replacing any earlier signing entries
    $lines = @()
    if (Test-Path $propsPath) {
        # @() keeps this an array even when the file has a single line
        $lines = @(Get-Content $propsPath | Where-Object { $_ -notmatch '^\s*RELEASE_(STORE_FILE|STORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)\s*=' -and $_ -notmatch '^# Release signing' })
    }
    $lines += ""
    $lines += "# Release signing (this file is gitignored)"
    $lines += "RELEASE_STORE_FILE=$keystoreName"
    $lines += "RELEASE_STORE_PASSWORD=$(Escape-Prop $password)"
    $lines += "RELEASE_KEY_ALIAS=$(Escape-Prop $alias)"
    $lines += "RELEASE_KEY_PASSWORD=$(Escape-Prop $password)"
    # Java reads .properties as Latin-1/UTF-8 without BOM; avoid PowerShell 5's BOM
    [System.IO.File]::WriteAllLines($propsPath, $lines, (New-Object System.Text.UTF8Encoding $false))

    Write-Host "`nKeystore created: $keystorePath" -ForegroundColor Green
    Write-Host "Signing details written to: $propsPath`n" -ForegroundColor Green

    Write-Host "Certificate fingerprints (add the SHA-1 and SHA-256 to Firebase if asked):" -ForegroundColor Cyan
    & $keytool -list -v -keystore $keystorePath -alias $alias -storepass:env ARKHIME_KS_PASS |
        Select-String -Pattern "SHA1:|SHA256:" | ForEach-Object { "  " + $_.Line.Trim() }
}
finally {
    Remove-Item Env:ARKHIME_KS_PASS -ErrorAction SilentlyContinue
}

Write-Host "`nIMPORTANT:" -ForegroundColor Yellow
Write-Host "  1. Back up $keystoreName and the password somewhere safe (password manager + cloud drive)."
Write-Host "     If you lose either, you can never publish an update that installs over existing copies."
Write-Host "  2. Never commit the .jks or local.properties (both are already gitignored)."
Write-Host "  3. For GitHub Actions signing, the KEYSTORE_FILE secret is the base64 of the .jks:"
Write-Host "     [Convert]::ToBase64String([IO.File]::ReadAllBytes('$keystorePath')) | Set-Clipboard"
