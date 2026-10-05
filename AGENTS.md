# AGENTS.md — nous-platform

## Build / test commands (Windows, PowerShell)

Repo root: `C:\Users\skillzor\IdeaProjects\nous\nous-platform`

Common Gradle tasks:
- compile app: `:composeApp:compileKotlinJvm`
- tests: `:platform-core:jvmTest :public-api:api-market:jvmTest :features:chart:jvmTest :features:trading:jvmTest :features:dom:jvmTest`
- module compile: `:features:chart:compileKotlinJvm`, `:features:trading:compileKotlinJvm`, etc.

### IMPORTANT: never run `gradlew` synchronously in the shell tool

A direct/pipe invocation like `.\gradlew.bat ... 2>&1 | Select-String ...` hangs the
shell until the Gradle daemon is killed (the daemon starts inside the tool's job
object / holds output handles, so EOF never arrives). **Always run Gradle detached
via WMI and poll the log file:**

```powershell
$repo = "C:\Users\skillzor\IdeaProjects\nous\nous-platform"
$log  = "C:\Temp\opencode\gw.log"
Remove-Item $log -ErrorAction SilentlyContinue
$cmd = "cmd.exe /c cd /d $repo && gradlew.bat <TASKS> --console=plain > $log 2>&1"
([wmiclass]'Win32_Process').Create($cmd) | Out-Null
$deadline = (Get-Date).AddSeconds(600)
$done = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 4
    try { $txt = Get-Content $log -Raw -ErrorAction Stop } catch { continue }
    if ($txt -match "BUILD SUCCESSFUL|BUILD FAILED") { $done = $true; break }
}
"done=$done"
Select-String -Path $log -Pattern "^e: |FAILED|BUILD " -ErrorAction SilentlyContinue |
    ForEach-Object { $_.Line }
```

If the poll loop itself hits the tool timeout, the build keeps running detached —
just re-read `$log` in the next call. The daemon may keep writing to the log; read
it with `Get-Content` (handles the shared lock) and retry on IOException.

### Run the app the same way (never blocks)

```powershell
$cmd = "cmd.exe /c cd /d $repo && gradlew.bat :composeApp:run --console=plain > C:\Temp\opencode\app-run.log 2>&1"
([wmiclass]'Win32_Process').Create($cmd) | Out-Null
# wait for the window process with MainWindowTitle matching "Nous Platform"
```

## Conventions
- UI панелей графика: стиль DrawingToolPanel (бордер ChartColors.gridLine, фон, скругление 8dp).
- Цены всегда форматировать/округлять через `SymbolFormatter` (`formatPrice`, `roundPrice`).
- Paper/real источник данных — `PaperTrading.enabledFlow` + `Provider.effectiveTrading()`.
