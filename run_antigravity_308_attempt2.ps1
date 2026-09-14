& {
    $ErrorActionPreference = 'Stop'

    $agy = "$env:LOCALAPPDATA\agy\bin\agy.exe"
    $repoUrl = 'https://github.com/SarShalomSimchi/github-profile-reviewer.git'
    $seedBranch = 'devex/antigravity-308-seed-v3'
    $attemptBranch = 'devex/antigravity-308-attempt2'
    $root = 'C:\AI-Sandboxes'
    $work = Join-Path $root 'antigravity-308-attempt2-repo'
    $gitBenchmark = Join-Path $work 'antigravity_308'
    $benchmark = Join-Path $root 'antigravity-308-attempt2-model'
    $stdoutFile = Join-Path $root 'antigravity-308-attempt2.stdout.json'
    $stderrFile = Join-Path $root 'antigravity-308-attempt2.stderr.txt'
    $preflightStdoutFile = Join-Path $root 'antigravity-308-attempt2-preflight.stdout.json'
    $preflightStderrFile = Join-Path $root 'antigravity-308-attempt2-preflight.stderr.txt'

    $settingsDir = Join-Path $env:USERPROFILE '.gemini\antigravity-cli'
    $settingsPath = Join-Path $settingsDir 'settings.json'
    $settingsBackupPath = Join-Path $settingsDir 'settings.json.antigravity-308.backup'
    $settingsExisted = $false
    $originalSettingsBase64 = $null
    $settingsTemporarilyChanged = $false
    $attemptStarted = $false

    function Invoke-GitChecked {
        param(
            [string]$Directory,
            [Parameter(ValueFromRemainingArguments = $true)]
            [string[]]$GitArgs
        )
        & git -C $Directory @GitArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Git failed: $($GitArgs -join ' ')"
        }
    }

    function Restore-OwnerSettings {
        if (-not $settingsTemporarilyChanged) {
            return
        }

        if ($settingsExisted) {
            if (-not (Test-Path $settingsBackupPath -PathType Leaf)) {
                throw 'Owner settings backup is missing; refusing to continue.'
            }
            $backupBytes = [IO.File]::ReadAllBytes($settingsBackupPath)
            [IO.File]::WriteAllBytes($settingsPath, $backupBytes)
            $restoredBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($settingsPath))
            if ($restoredBase64 -ne $originalSettingsBase64) {
                throw 'Owner settings restoration was not byte-for-byte identical.'
            }
            Remove-Item -LiteralPath $settingsBackupPath -Force
        } else {
            if (Test-Path $settingsPath -PathType Leaf) {
                Remove-Item -LiteralPath $settingsPath -Force
            }
            if (Test-Path $settingsBackupPath -PathType Leaf) {
                Remove-Item -LiteralPath $settingsBackupPath -Force
            }
        }

        $settingsTemporarilyChanged = $false
        Write-Host 'Owner Antigravity settings restored byte-for-byte.' -ForegroundColor Green
    }

    try {
        Write-Host '=== #308 ATTEMPT 2 PRECONDITIONS (NO MODEL CALL) ===' -ForegroundColor Cyan

        if (-not (Test-Path $agy -PathType Leaf)) {
            throw "Antigravity CLI not found at $agy"
        }

        $versionText = (& $agy --version 2>&1 | Out-String).Trim()
        Write-Host "Antigravity CLI: $versionText"
        $versionMatch = [Regex]::Match($versionText, '(\d+)\.(\d+)\.(\d+)')
        if (-not $versionMatch.Success) {
            throw 'Could not verify Antigravity CLI version. Final attempt not started.'
        }
        $version = [Version]::new(
            [int]$versionMatch.Groups[1].Value,
            [int]$versionMatch.Groups[2].Value,
            [int]$versionMatch.Groups[3].Value
        )
        if ($version -lt [Version]::new(1, 2, 0)) {
            throw "Antigravity CLI $version is older than the configuration baseline used for this final test. Final attempt not started."
        }

        New-Item -ItemType Directory -Force -Path $root | Out-Null

        if (Test-Path $work) {
            throw "Attempt 2 Git workspace already exists: $work. Final attempt not started."
        }
        if (Test-Path $benchmark) {
            throw "Attempt 2 model workspace already exists: $benchmark. Final attempt not started."
        }

        if (Test-Path $settingsBackupPath -PathType Leaf) {
            throw "A prior Antigravity settings backup already exists at $settingsBackupPath. Inspect/restore it before the final attempt."
        }

        $existingRemote = & git ls-remote --heads $repoUrl "refs/heads/$attemptBranch"
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not verify the remote Attempt 2 branch state. Final attempt not started.'
        }
        if ($existingRemote) {
            throw 'Remote Attempt 2 branch already exists. Final attempt not started.'
        }

        Write-Host '=== PREPARE PUBLIC/SYNTHETIC WORKSPACE ===' -ForegroundColor Cyan

        & git clone --filter=blob:none --no-checkout --branch $seedBranch --single-branch $repoUrl $work
        if ($LASTEXITCODE -ne 0) {
            throw 'Synthetic public repository clone failed. Final attempt not started.'
        }

        Invoke-GitChecked -Directory $work -GitArgs @('sparse-checkout', 'init', '--cone')
        Invoke-GitChecked -Directory $work -GitArgs @('sparse-checkout', 'set', 'antigravity_308')
        Invoke-GitChecked -Directory $work -GitArgs @('checkout', $seedBranch)
        Invoke-GitChecked -Directory $work -GitArgs @('switch', '-c', $attemptBranch)

        if (-not (Test-Path (Join-Path $gitBenchmark 'TASK.md') -PathType Leaf)) {
            throw 'Synthetic TASK.md is missing from the public seed. Final attempt not started.'
        }
        if (-not (Test-Path (Join-Path $gitBenchmark 'expiry_cache.py') -PathType Leaf)) {
            throw 'Synthetic expiry_cache.py is missing from the public seed. Final attempt not started.'
        }

        New-Item -ItemType Directory -Path $benchmark | Out-Null
        Copy-Item -LiteralPath (Join-Path $gitBenchmark 'TASK.md') -Destination (Join-Path $benchmark 'TASK.md')
        Copy-Item -LiteralPath (Join-Path $gitBenchmark 'expiry_cache.py') -Destination (Join-Path $benchmark 'expiry_cache.py')

        $taskHashBefore = (Get-FileHash -LiteralPath (Join-Path $benchmark 'TASK.md') -Algorithm SHA256).Hash
        $sourceHashBefore = (Get-FileHash -LiteralPath (Join-Path $benchmark 'expiry_cache.py') -Algorithm SHA256).Hash

        Write-Host '=== VALIDATION / GITHUB HANDOFF PREFLIGHT (NO MODEL CALL) ===' -ForegroundColor Cyan

        $pythonCommand = Get-Command python -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $pythonCommand) {
            throw 'Python is unavailable for trusted validation. Final attempt not started.'
        }

        Push-Location $benchmark
        try {
            & python -c "from expiry_cache import ExpiryCache; c=ExpiryCache(); c.set('k','v',0,now=1.0); assert c.get('k',now=1.0) == 'v'"
            if ($LASTEXITCODE -ne 0) {
                throw 'Synthetic baseline is not in the expected intentionally-failing boundary state. Final attempt not started.'
            }
        } finally {
            Pop-Location
        }

        & git -C $work push --dry-run origin "HEAD:refs/heads/$attemptBranch"
        if ($LASTEXITCODE -ne 0) {
            throw 'GitHub write handoff dry-run failed. Final attempt not started.'
        }

        $remoteAfterDryRun = & git -C $work ls-remote --heads origin "refs/heads/$attemptBranch"
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not verify dry-run side effects. Final attempt not started.'
        }
        if ($remoteAfterDryRun) {
            throw 'Dry-run unexpectedly created the Attempt 2 branch. Final attempt not started.'
        }

        Write-Host 'Validation environment and GitHub write handoff preflight PASS.' -ForegroundColor Green
        Write-Host '=== INSTALL TEMPORARY NARROW PERMISSIONS ===' -ForegroundColor Cyan

        New-Item -ItemType Directory -Force -Path $settingsDir | Out-Null
        $settingsExisted = Test-Path $settingsPath -PathType Leaf

        if ($settingsExisted) {
            $originalBytes = [IO.File]::ReadAllBytes($settingsPath)
            $originalSettingsBase64 = [Convert]::ToBase64String($originalBytes)
            [IO.File]::WriteAllBytes($settingsBackupPath, $originalBytes)
        }

        $permissionPath = $benchmark.Replace('\', '/')
        $temporarySettings = [ordered]@{
            enableTerminalSandbox = $true
            toolPermission = 'request-review'
            trustedWorkspaces = @($permissionPath)
            permissions = [ordered]@{
                allow = @(
                    "read_file($permissionPath)",
                    "write_file($permissionPath)"
                )
                ask = @(
                    'command(*)',
                    'read_url(*)',
                    'execute_url(*)',
                    'mcp(*)'
                )
            }
        }

        $temporaryJson = $temporarySettings | ConvertTo-Json -Depth 8
        [IO.File]::WriteAllText($settingsPath, $temporaryJson, [Text.UTF8Encoding]::new($false))
        $settingsTemporarilyChanged = $true

        $effective = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
        $effectiveAllow = @($effective.permissions.allow)
        $expectedAllow = @(
            "read_file($permissionPath)",
            "write_file($permissionPath)"
        )

        if ($effective.enableTerminalSandbox -ne $true) {
            throw 'Temporary sandbox setting is not enabled. Final attempt not started.'
        }
        if ([string]$effective.toolPermission -ne 'request-review') {
            throw 'Temporary tool permission mode is not request-review. Final attempt not started.'
        }
        if ($effectiveAllow.Count -ne 2) {
            throw 'Temporary allow-list is not exactly two scoped file rules. Final attempt not started.'
        }
        foreach ($rule in $expectedAllow) {
            if ($rule -notin $effectiveAllow) {
                throw "Missing scoped permission: $rule. Final attempt not started."
            }
        }
        foreach ($rule in $effectiveAllow) {
            if ($rule -match 'command\(|read_url\(|execute_url\(|mcp\(|unsandboxed\(' -or $rule -match '\(\*\)') {
                throw "Dangerous or broad allow-rule detected: $rule. Final attempt not started."
            }
        }

        Write-Host '=== READ-ONLY HEADLESS PERMISSION PREFLIGHT (NO MODEL TURN) ===' -ForegroundColor Cyan

        Push-Location $benchmark
        try {
            & $agy --sandbox -p '/permissions' --output-format json --print-timeout 1m 1> $preflightStdoutFile 2> $preflightStderrFile
            $permissionExit = $LASTEXITCODE
        } finally {
            Pop-Location
        }

        if ($permissionExit -ne 0) {
            throw 'Antigravity permission preflight failed. Final attempt not started.'
        }

        $permissionRaw = (Get-Content -LiteralPath $preflightStdoutFile -Raw).Trim()
        $permissionResult = $permissionRaw | ConvertFrom-Json
        if ([string]$permissionResult.status -ne 'SUCCESS') {
            throw 'Antigravity permission preflight did not return SUCCESS. Final attempt not started.'
        }
        if (($permissionResult.PSObject.Properties.Name -contains 'num_turns') -and [int]$permissionResult.num_turns -ne 0) {
            throw 'Permission preflight unexpectedly consumed a model turn. Final attempt not started.'
        }
        if (($permissionResult.PSObject.Properties.Name -contains 'usage') -and
            ($permissionResult.usage.PSObject.Properties.Name -contains 'total_tokens') -and
            [int64]$permissionResult.usage.total_tokens -ne 0) {
            throw 'Permission preflight unexpectedly consumed tokens. Final attempt not started.'
        }
        if ($permissionRaw -notmatch [Regex]::Escape($permissionPath)) {
            throw 'Antigravity did not report the scoped benchmark permission. Final attempt not started.'
        }

        Write-Host 'Preflight PASS: scoped file permissions only; sandbox requested; no dangerous bypass.' -ForegroundColor Green
        Write-Host '=== ANTIGRAVITY #308 ATTEMPT 2 START ===' -ForegroundColor Magenta
        $attemptStarted = $true

        $prompt = @'
You are executing Google Antigravity coding benchmark #308 Attempt 2, the final bounded attempt.

SECURITY AND SCOPE:
- Your entire workspace is the public synthetic antigravity_308 benchmark directory.
- Use file read/edit/write tools only inside this workspace.
- Do not access parent directories or any path outside this workspace.
- Do not execute terminal or shell commands.
- Do not use browser, web, URLs, network, MCP, plugins, credentials, secrets, or external services.
- Do not change TASK.md.
- A trusted outer harness will run tests, Git operations, validation, and GitHub handoff after you stop.

TASK:
1. Read TASK.md and expiry_cache.py.
2. Fix the exact-expiry-boundary defect in ExpiryCache.
3. Add focused standard-library unittest regression tests under tests/.
4. Cover before expiry, exact expiry, after expiry, and zero-TTL behavior.
5. Keep the implementation minimal.
6. Produce a real non-empty source/test diff.
7. Do not commit or push.

When edits are complete, summarize exactly which files you changed and stop.
'@

        Push-Location $benchmark
        try {
            & $agy --sandbox -p $prompt --output-format json --print-timeout 10m 1> $stdoutFile 2> $stderrFile
            $agyExit = $LASTEXITCODE
        } finally {
            Pop-Location
            Restore-OwnerSettings
        }

        Write-Host '=== ANTIGRAVITY STDOUT ==='
        if (Test-Path $stdoutFile) {
            Get-Content -LiteralPath $stdoutFile
        }
        Write-Host '=== ANTIGRAVITY STDERR ==='
        if (Test-Path $stderrFile) {
            Get-Content -LiteralPath $stderrFile
        }

        if ($agyExit -ne 0) {
            throw "Antigravity Attempt 2 exited with code $agyExit."
        }

        $result = Get-Content -LiteralPath $stdoutFile -Raw | ConvertFrom-Json
        if ([string]$result.status -ne 'SUCCESS') {
            throw "Antigravity Attempt 2 status was $($result.status)."
        }

        Write-Host '=== TRUSTED MODEL-WORKSPACE SCOPE CHECK ===' -ForegroundColor Cyan

        $modelFiles = @(
            Get-ChildItem -LiteralPath $benchmark -File -Recurse |
                ForEach-Object {
                    $_.FullName.Substring($benchmark.Length).TrimStart('\\').Replace('\\', '/')
                } |
                Sort-Object -Unique
        )

        $unexpectedModelFiles = @(
            $modelFiles | Where-Object {
                $_ -ne 'TASK.md' -and
                $_ -ne 'expiry_cache.py' -and
                $_ -notlike 'tests/test_*.py'
            }
        )
        if ($unexpectedModelFiles.Count -gt 0) {
            throw "Out-of-scope model-workspace files detected: $($unexpectedModelFiles -join ', ')"
        }

        $taskHashAfter = (Get-FileHash -LiteralPath (Join-Path $benchmark 'TASK.md') -Algorithm SHA256).Hash
        if ($taskHashAfter -ne $taskHashBefore) {
            throw 'TASK.md was modified by the model.'
        }

        $sourceHashAfter = (Get-FileHash -LiteralPath (Join-Path $benchmark 'expiry_cache.py') -Algorithm SHA256).Hash
        if ($sourceHashAfter -eq $sourceHashBefore) {
            throw 'Attempt 2 produced no source change.'
        }

        $modelTests = @(
            $modelFiles | Where-Object { $_ -like 'tests/test_*.py' }
        )
        if ($modelTests.Count -eq 0) {
            throw 'No focused regression test file was created.'
        }

        Copy-Item -LiteralPath (Join-Path $benchmark 'expiry_cache.py') -Destination (Join-Path $gitBenchmark 'expiry_cache.py') -Force
        $gitTests = Join-Path $gitBenchmark 'tests'
        New-Item -ItemType Directory -Force -Path $gitTests | Out-Null
        foreach ($relativeTest in $modelTests) {
            $sourceTest = Join-Path $benchmark ($relativeTest.Replace('/', '\\'))
            $destinationTest = Join-Path $gitBenchmark ($relativeTest.Replace('/', '\\'))
            $destinationDir = Split-Path -Parent $destinationTest
            New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null
            Copy-Item -LiteralPath $sourceTest -Destination $destinationTest -Force
        }

        $tracked = @(
            & git -C $work diff --name-only |
                Where-Object { $_ -and $_.Trim() } |
                ForEach-Object { $_.Trim() }
        )
        $untracked = @(
            & git -C $work ls-files --others --exclude-standard |
                Where-Object { $_ -and $_.Trim() } |
                ForEach-Object { $_.Trim() }
        )
        $changed = @($tracked + $untracked | Sort-Object -Unique)
        $unexpectedGitFiles = @(
            $changed | Where-Object {
                $_ -ne 'antigravity_308/expiry_cache.py' -and
                $_ -notlike 'antigravity_308/tests/test_*.py'
            }
        )
        if ($unexpectedGitFiles.Count -gt 0) {
            throw "Out-of-scope Git changes detected: $($unexpectedGitFiles -join ', ')"
        }

        Write-Host '=== TRUSTED DETERMINISTIC VALIDATION ===' -ForegroundColor Cyan

        $previousNoBytecode = $env:PYTHONDONTWRITEBYTECODE
        $env:PYTHONDONTWRITEBYTECODE = '1'
        Push-Location $work
        try {
            & python -m unittest discover -s antigravity_308/tests -p 'test_*.py' -v
            if ($LASTEXITCODE -ne 0) {
                throw 'Synthetic unittest validation failed.'
            }
            & git diff --check
            if ($LASTEXITCODE -ne 0) {
                throw 'git diff --check failed.'
            }
        } finally {
            Pop-Location
            $env:PYTHONDONTWRITEBYTECODE = $previousNoBytecode
        }

        Write-Host '=== DIRECT GITHUB HANDOFF ===' -ForegroundColor Cyan

        Invoke-GitChecked -Directory $work -GitArgs @('add', 'antigravity_308/expiry_cache.py', 'antigravity_308/tests')

        $staged = @(
            & git -C $work diff --cached --name-only |
                Where-Object { $_ -and $_.Trim() } |
                ForEach-Object { $_.Trim() }
        )
        $badStaged = @(
            $staged | Where-Object {
                $_ -ne 'antigravity_308/expiry_cache.py' -and
                $_ -notlike 'antigravity_308/tests/test_*.py'
            }
        )
        if ($badStaged.Count -gt 0) {
            throw "Unexpected staged files: $($badStaged -join ', ')"
        }

        & git -C $work -c user.name='SarShalomSimchi' -c user.email='39561011+SarShalomSimchi@users.noreply.github.com' commit -m 'Antigravity #308 synthetic attempt 2'
        if ($LASTEXITCODE -ne 0) {
            throw 'Attempt 2 commit failed.'
        }

        $validatedHead = (& git -C $work rev-parse HEAD).Trim()

        & git -C $work push origin "HEAD:refs/heads/$attemptBranch"
        if ($LASTEXITCODE -ne 0) {
            throw 'Attempt 2 GitHub branch push failed.'
        }

        $remoteLine = & git -C $work ls-remote origin "refs/heads/$attemptBranch"
        if (-not $remoteLine) {
            throw 'Attempt 2 remote branch verification failed.'
        }
        $remoteHead = ($remoteLine -split '\s+')[0]
        if ($remoteHead -ne $validatedHead) {
            throw 'Attempt 2 remote head does not match the validated local head.'
        }

        Write-Host "ANTIGRAVITY #308 ATTEMPT 2 CODING PASS head=$validatedHead" -ForegroundColor Green
        Write-Host 'Branch pushed. Development Agent Enablement owns PR, exact-head CI, and independent review next.' -ForegroundColor Green
    }
    catch {
        $primaryError = $_.Exception.Message
        try {
            Restore-OwnerSettings
        } catch {
            Write-Host "CRITICAL: settings restoration problem: $($_.Exception.Message)" -ForegroundColor Red
        }

        if ($attemptStarted) {
            Write-Host "ANTIGRAVITY #308 ATTEMPT 2 STOPPED AFTER MODEL START: $primaryError" -ForegroundColor Red
        } else {
            Write-Host "ANTIGRAVITY #308 PRECONDITION STOP: $primaryError" -ForegroundColor Yellow
            Write-Host 'Attempt 2 model call was not started.' -ForegroundColor Yellow
        }

        if (Test-Path $work) {
            Write-Host "Git evidence workspace preserved at: $work" -ForegroundColor Yellow
        }
        if (Test-Path $benchmark) {
            Write-Host "Model evidence workspace preserved at: $benchmark" -ForegroundColor Yellow
        }
    }
}
