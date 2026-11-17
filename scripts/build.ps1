param(
    [switch]$Release,
    [string]$SigningConfig = "$env:USERPROFILE\.android\quiet-eq\signing.xml"
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if ($Release) {
        if (!(Test-Path -LiteralPath $SigningConfig)) { throw "Missing private signing configuration: $SigningConfig. Set up your own release key as described in README.md." }
        $signing = Import-Clixml -LiteralPath $SigningConfig
        $env:QUIET_EQ_KEYSTORE = $signing.KeyStore
        $env:QUIET_EQ_KEY_ALIAS = $signing.Alias
        $env:QUIET_EQ_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $signing.Password).Password
        $env:QUIET_EQ_KEY_PASSWORD = $env:QUIET_EQ_STORE_PASSWORD
        & .\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --console=plain
    } else {
        & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
    }
    if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
} finally {
    if ($Release) {
        Remove-Item Env:\QUIET_EQ_STORE_PASSWORD, Env:\QUIET_EQ_KEY_PASSWORD -ErrorAction SilentlyContinue
    }
    Pop-Location
}
