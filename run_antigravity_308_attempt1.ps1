& {
  $ErrorActionPreference='Stop'
  $agy="$env:LOCALAPPDATA\agy\bin\agy.exe"
  $repo='https://github.com/SarShalomSimchi/github-profile-reviewer.git'
  $seed='devex/antigravity-308-seed-v2'
  $seedParent='aeb92e984346c578e153cc667d33f3682f661348'
  $attempt='devex/antigravity-308-attempt1'
  $root='C:\AI-Sandboxes'
  $work=Join-Path $root 'antigravity-308-attempt1'
  $out=Join-Path $root 'antigravity-308-attempt1.json'
  $err=Join-Path $root 'antigravity-308-attempt1.stderr.txt'
  try {
    if(!(Test-Path $agy)){throw "Antigravity CLI not found: $agy"}
    New-Item -ItemType Directory -Force -Path $root|Out-Null
    if(Test-Path $work){throw "Workspace already exists: $work"}

    Write-Host "=== PRECHECK permissions ===" -ForegroundColor Cyan
    $perm=(& $agy -p '/permissions' --output-format json 2>&1|Out-String)
    if($LASTEXITCODE-ne 0){throw 'Could not inspect Antigravity permissions'}
    Write-Host $perm
    foreach($x in @('read_file\(\*\)','write_file\(\*\)','command\(\*\)','read_url\(\*\)','execute_url\(\*\)','unsandboxed\(\*\)','mcp\(\*\)')){
      if($perm-match$x){throw "Broad persistent permission detected: $x. Attempt not started."}
    }

    Write-Host "=== PREPARE public synthetic workspace ===" -ForegroundColor Cyan
    git clone --filter=blob:none --no-checkout --branch $seed --single-branch $repo $work
    if($LASTEXITCODE-ne 0){throw 'Clone failed'}
    git -C $work sparse-checkout init --cone
    git -C $work sparse-checkout set antigravity_308
    git -C $work checkout $seed
    if($LASTEXITCODE-ne 0){throw 'Sparse checkout failed'}
    $parent=(git -C $work rev-parse 'HEAD^').Trim()
    if($parent-ne$seedParent){throw "Unexpected seed lineage: $parent"}
    $seedDelta=@(git -C $work diff --name-only HEAD^ HEAD|?{$_})
    if($seedDelta.Count-ne 1 -or $seedDelta[0]-ne 'run_antigravity_308_attempt1.ps1'){throw 'Seed changed beyond runner commit'}
    $remote=git -C $work ls-remote --heads origin "refs/heads/$attempt"
    if($remote){throw 'Attempt 1 remote branch already exists'}
    git -C $work switch -c $attempt
    if($LASTEXITCODE-ne 0){throw 'Could not create attempt branch'}

    Write-Host "=== MODEL / QUOTA BEFORE ===" -ForegroundColor Cyan
    & $agy -p '/model' --output-format json
    & $agy -p '/quota' --output-format json

    Write-Host "=== ANTIGRAVITY ATTEMPT 1 START ===" -ForegroundColor Magenta
    $prompt=@'
Execute Google Antigravity coding benchmark #308 Attempt 1.
This workspace is public and synthetic only.
Do not access paths outside this workspace. Do not use terminal/shell commands, browser, web, network, URLs, MCP, credentials, secrets, or external services. Do not modify TASK.md. Only read/edit files under antigravity_308/. A trusted outer harness will run tests and Git/GitHub operations.
Read antigravity_308/TASK.md and antigravity_308/expiry_cache.py. Fix the exact-expiry-boundary defect. Add focused standard-library unittest regression tests under antigravity_308/tests/ covering before expiry, exact expiry, after expiry, and zero TTL. Keep changes minimal. Do not commit or push. Produce a real non-empty source/test diff, summarize changed files, and stop.
'@
    Push-Location $work
    try { & $agy -p $prompt --output-format json --print-timeout 10m 1>$out 2>$err; $code=$LASTEXITCODE }
    finally { Pop-Location }
    if(Test-Path $out){Get-Content $out}
    if(Test-Path $err){Get-Content $err}
    if($code-ne 0){throw "Antigravity exited $code"}
    $r=Get-Content $out -Raw|ConvertFrom-Json
    if($r.status-ne'SUCCESS'){throw "Antigravity status: $($r.status)"}

    Write-Host "=== VERIFY DIFF ===" -ForegroundColor Cyan
    $changed=@((git -C $work diff --name-only)+(git -C $work ls-files --others --exclude-standard)|?{$_}|Sort-Object -Unique)
    $changed|%{Write-Host "  $_"}
    if($changed.Count-eq 0){throw 'No file changes'}
    $bad=@($changed|?{$_-ne'antigravity_308/expiry_cache.py'-and$_-notlike'antigravity_308/tests/*'})
    if($bad){throw "Out-of-scope changes: $($bad -join ', ')"}
    if('antigravity_308/expiry_cache.py'-notin$changed){throw 'Source file unchanged'}
    if(-not($changed|?{$_-like'antigravity_308/tests/test_*.py'})){throw 'No regression test file'}

    Write-Host "=== DETERMINISTIC VALIDATION ===" -ForegroundColor Cyan
    Push-Location $work
    try {
      $old=$env:PYTHONDONTWRITEBYTECODE; $env:PYTHONDONTWRITEBYTECODE='1'
      python -m unittest discover -s antigravity_308/tests -p 'test_*.py' -v
      if($LASTEXITCODE-ne 0){throw 'unittest failed'}
      python -m py_compile antigravity_308/expiry_cache.py
      if($LASTEXITCODE-ne 0){throw 'compile failed'}
      git diff --check
      if($LASTEXITCODE-ne 0){throw 'diff-check failed'}
    } finally { $env:PYTHONDONTWRITEBYTECODE=$old; Pop-Location }

    Write-Host "=== COMMIT / DIRECT GITHUB HANDOFF ===" -ForegroundColor Cyan
    git -C $work add antigravity_308/expiry_cache.py antigravity_308/tests
    git -C $work -c user.name='SarShalomSimchi' -c user.email='39561011+SarShalomSimchi@users.noreply.github.com' commit -m 'Antigravity #308 synthetic attempt 1'
    if($LASTEXITCODE-ne 0){throw 'Commit failed'}
    $head=(git -C $work rev-parse HEAD).Trim()
    git -C $work push origin "HEAD:refs/heads/$attempt"
    if($LASTEXITCODE-ne 0){throw 'Push failed'}
    $remoteHead=((git -C $work ls-remote origin "refs/heads/$attempt")-split '\s+')[0]
    if($remoteHead-ne$head){throw 'Remote head mismatch'}

    Write-Host "=== QUOTA AFTER ===" -ForegroundColor Cyan
    & $agy -p '/quota' --output-format json
    Write-Host "ANTIGRAVITY #308 ATTEMPT 1 CODING PASS head=$head" -ForegroundColor Green
  }
  catch {
    Write-Host "ANTIGRAVITY #308 ATTEMPT 1 STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    if(Test-Path $work){Write-Host "Evidence workspace: $work" -ForegroundColor Yellow}
  }
}
