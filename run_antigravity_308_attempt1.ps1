& {
    $ErrorActionPreference = 'Stop'

    $agy = "$env:LOCALAPPDATA\agy\bin\agy.exe"
    $repoUrl = 'https://github.com/SarShalomSimchi/github-profile-reviewer.git'
    $seedBranch = 'devex/antigravity-308-seed'
    $expectedParentSeed = 'aeb92e984346c578e153cc667d33f3682f661348'
    $runnerPath = 'run_antigravity_308_attempt1.ps1'
    $attemptBranch = 'devex/antigravity-308-attempt1'
    $sandboxRoot = 'C:\AI-Sandboxes'
    $work = Join-Path $sandboxRoot 'antigravity-308-attempt1'
    $stdoutFile = Join-Path $sandboxRoot 'antigravity-308-attempt1.stdout.json'
    $stderrFile = Join-Path $sandboxRoot 'antigravity-308-attempt1.stderr.txt'

    function Invoke-Git {
        param(
            [string]$WorkingDirectory,
            [Parameter(ValueFromRemainingArguments = $true)]
            [string[]]$GitArgs
        )
        & git -C $WorkingDirectory @GitArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Git command failed: git -C `"$WorkingDirectory`" $($GitArgs -join ' ')"
        }
    }

    try {
        Write-Host "`n=== 1. Preconditions ===" -ForegroundColor Cyan

        if (-not (Test-Path $agy)) {
            throw "Antigravity CLI not found at $agy"
        }

        New-Item -ItemType Directory -Force -Path $sandboxRoot | Out-Null

        if (Test-Path $work) {
            throw "Attempt workspace already exists: $work"
        }

        Write-Host "`n=== 2. Read-only permission preflight ===" -ForegroundColor Cyan
        $permissionsRaw = (& $agy -p "/permissions" --output-format json 2>&1 | Out-String)

        if ($LASTEXITCODE -ne 0) {
            throw "Could not inspect Antigravity permissions."
        }

        Write-Host $permissionsRaw

        $dangerousPatterns = @(
            'read_file\(\*\)',
            'write_file\(\*\)',
            'command\(\*\)',
            'read_url\(\*\)',
            'execute_url\(\*\)',
            'unsandboxed\(\*\)',
            'mcp\(\*\)'
        )

        foreach ($pattern in $dangerousPatterns) {
            if ($permissionsRaw -match $pattern) {
                throw "Broad persistent Antigravity permission detected: $pattern. Attempt NOT started."
            }
        }

        Write-Host "`n=== 3. Clone PUBLIC synthetic benchmark ===" -ForegroundColor Cyan

        & git clone --filter=blob:none --no-checkout --branch $seedBranch --single-branch $repoUrl $work
        if ($LASTEXITCODE -ne 0) {
            throw "Synthetic repository clone failed."
        }

        Invoke-Git $work sparse-checkout init --cone
        Invoke-Git $work sparse-checkout set antigravity_308
        Invoke-Git $work checkout $seedBranch

        $seedHead = (git -C $work rev-parse HEAD).Trim()
        $parentHead = (git -C $work rev-parse 'HEAD^').Trim()

        Write-Host "Seed HEAD:   $seedHead"
        Write-Host "Seed parent: $parentHead"

        if ($parentHead -ne $expectedParentSeed) {
            throw "Seed lineage changed unexpectedly. Expected parent $expectedParentSeed but found $parentHead."
        }

        $runnerCommitFiles = @(
            git -C $work diff --name-only HEAD^ HEAD |
                Where-Object { $_ -and $_.Trim() } |
                ForEach-Object { $_.Trim() }
        )

        if ($runnerCommitFiles.Count -ne 1 -or $runnerCommitFiles[0] -ne $runnerPath) {
            throw "Seed branch changed by more than the approved runner commit."
        }

        if (-not (Test-Path (Join-Path $work 'antigravity_308\TASKY	ÊJJHÂÝÈÞ[]XÈTÒËY\ÈZ\ÜÚ[ËBY
[Ý
\ÝT]
Ú[T]	ÛÜÈ	Ø[YÜ]]WÌÌ^\WØØXÚKIÊJJHÂÝÈÞ[]XÈÛÝ\ÙH[H\ÈZ\ÜÚ[ËB	^\Ý[Ô[[ÝHHÚ]PÈ	ÛÜÈË\[[ÝHKZXYÈÜYÚ[YËÚXYËÉ][\[ÚY
	TÕVUÓÑH[H
HÂÝÈÛÝ[ÝÚXÚÈ[[ÝH][\[ÚBY
	^\Ý[Ô[[ÝJHÂÝÈ[[ÝH][\H[Ú[XYH^\ÝËÈÝÝ\Ü]H]BÜ]KRÜÝOOHÜX]H\ÛÛ]Y][\H[ÚOOHQÜYÜÝ[ÛÛÜÞX[[ÚÙKQÚ]	ÛÜÈÝÚ]ÚXÈ	][\[ÚÜ]KRÜÝOOHKXÛÜ[Ù[[][ÝHYÜH][\HOOHQÜYÜÝ[ÛÛÜÞX[		YÞH\Û[Ù[K[Ý]]YÜX]ÛÛ		YÞH\Ü][ÝHK[Ý]]YÜX]ÛÛÜ]KRÜÝOOHSQÔUUHUSTHÕTOOHQÜYÜÝ[ÛÛÜXYÙ[B	Û\H	Â[ÝH\H^XÝ][ÈÛÛÙÛH[YÜ]]HÛÙ[È[ÚX\ÈÌÌ][\KUPÖHSÐÓÔNH\ÈÛÜÜÜXÙHÛÛZ[ÈÛHHXXÈÞ[]XÈ[ÚX\ËHÈÝXØÙ\ÜÈ[H]Ý]ÚYH\ÈÛÜÜÜXÙKHÈÝ\ÙH\Z[[ÜÚ[ÛÛ[X[ËHÈÝ\ÙHÝÜÙ\ÙX]ÛÜËTËPÔ^\[Ù\XÙ\ËÜY[X[ËÙXÜ]ËÜ]]H]KHÈÝ[ÙYHTÒËYHÛHXYÜY][\È[\[YÜ]]WÌÌËHH\ÝYÝ]\\\ÜÈÚ[[\ÝËÚ]Ü\][ÛË[Y][Û[Ú]X[ÙY\[ÝHÝÜTÒÎKXY[YÜ]]WÌÌÕTÒËY[[YÜ]]WÌÌÙ^\WØØXÚKK^H^XÝY^\KXÝ[\HYXÝ[^\PØXÚKËYHØÝ\ÙYÝ[\[X\H[]\ÝYÜ\ÜÚ[ÛÝZ]H[\[YÜ]]WÌÌÝ\ÝËËÛÝ\]Z[[][NHYÜH^\HÝ[\NÂH^XÝ^\HÝ[\NÂHY\^\HÝ[\NÂH\È^\\È[[YYX][KKÙY\H[\[Y[][ÛZ[[X[[ØÛÜYÈÝÛÛ[Z]Ü\ÚËÙXÙHHX[ÛY[\HÛÝ\ÙKÝ\ÝYÚ[[HY]È\HÛÛ\]KÝ[[X\^H^XÝHÚXÚ[\È[ÝHÚ[ÙY[ÝÜÐ\ÚSØØ][Û	ÛÜÂHÂ		YÞH\	Û\K[Ý]]YÜX]ÛÛK\[][Y[Ý]LHO	ÝÝ][H	Ý\[B	YÞQ^]H	TÕVUÓÑBB[[HÂÜSØØ][ÛBÜ]KRÜÝKKH[YÜ]]HÝÝ]KKHY
\ÝT]	ÝÝ][JHÈÙ]PÛÛ[	ÝÝ][HBÜ]KRÜÝKKH[YÜ]]HÝ\KKHY
\ÝT]	Ý\[JHÈÙ]PÛÛ[	Ý\[HBY
	YÞQ^][H
HÂÝÈ[YÜ]]H][\HØÙ\ÜÈZ[YÚ]^]	YÞQ^]B	\Ý[HÙ]PÛÛ[	ÝÝ][HT]ÈÛÛ\ÛKRÛÛY
	\Ý[Ý]\È[H	ÔÕPÐÑTÔÉÊHÂÝÈ[YÜ]]H][\HYÝ]\ÕPÐÑTÔËÝ]\Î	
	\Ý[Ý]\ÊHBÜ]KRÜÝOOHË\YHÛY[\HØÛÜYYOOHQÜYÜÝ[ÛÛÜÞX[	XÚÙYH
Ú]PÈ	ÛÜÈYK[[YK[ÛHÚ\KSØXÝÈ	ÈX[	Ë[J
HHÜXXÚSØXÝÈ	Ë[J
HB
B	[XÚÙYH
Ú]PÈ	ÛÜÈËY[\ÈK[Ý\ÈKY^ÛYK\Ý[\Ú\KSØXÝÈ	ÈX[	Ë[J
HHÜXXÚSØXÝÈ	Ë[J
HB
B	Ú[ÙYH
	XÚÙY
È	[XÚÙYÛÜSØXÝU[\]YJBÜ]KRÜÝÚ[ÙY[\Î	Ú[ÙYÜXXÚSØXÝÈÜ]KRÜÝ	ÈBY
	Ú[ÙYÛÝ[Y\H
HÂÝÈ][\HÙXÙYÈ[HÚ[Ù\ËB	[^XÝYH
	Ú[ÙYÚ\KSØXÝÂ	È[H	Ø[YÜ]]WÌÌÙ^\WØØXÚKIÈX[	È[ÝZÙH	Ø[YÜ]]WÌÌÝ\ÝËÊÂB
BY
	[^XÝYÛÝ[YÝ
HÂÜ]KRÜÝÝ][Ù\ØÛÜH[\ÎQÜYÜÝ[ÛÛÜY	[^XÝYÜXXÚSØXÝÈÜ]KRÜÝ	ÈQÜYÜÝ[ÛÛÜYBÝÈ][\HÚY[Y[HØÛÜKBY
	Ø[YÜ]]WÌÌÙ^\WØØXÚKIÈ[Ý[	Ú[ÙY
HÂÝÈÛÝ\ÙH[HØ\ÈÝÚ[ÙYB	\Ý[\ÈH
	Ú[ÙYÚ\KSØXÝÈ	È[ZÙH	Ø[YÜ]]WÌÌÝ\ÝËÝ\ÝÊIÈB
BY
	\Ý[\ËÛÝ[Y\H
HÂÝÈÈYÜ\ÜÚ[Û\Ý[HØ\ÈÜX]YBÜ]KRÜÝOOH]\Z[\ÝXÈ[Y][ÛOOHQÜYÜÝ[ÛÛÜÞX[	ÛÐ]XÛÙHH	[UÓÓÔUPUPÓÑB	[UÓÓÔUPUPÓÑHH	ÌIÂ\ÚSØØ][Û	ÛÜÂHÂ	]Û[H[]\Ý\ØÛÝ\\È[YÜ]]WÌÌÝ\ÝÈ\	Ý\ÝÊIÈ]Y
	TÕVUÓÑH[H
HÂÝÈYÜ\ÜÚ[Û\ÝÝZ]HZ[YB	]Û[HWØÛÛ\[H[YÜ]]WÌÌÙ^\WØØXÚKBY
	TÕVUÓÑH[H
HÂÝÈÛÝ\ÙHÛÛ\[HÚXÚÈZ[YB	Ú]YKXÚXÚÂY
	TÕVUÓÑH[H
HÂÝÈÚ]YKXÚXÚÈZ[YBB[[HÂÜSØØ][Û	[UÓÓÔUPUPÓÑHH	ÛÐ]XÛÙBBÜ]KRÜÝOOHKÛÛ[Z]XØÙ\YÛÝ\ÙKÝ\ÝYOOHQÜYÜÝ[ÛÛÜÞX[[ÚÙKQÚ]	ÛÜÈY[YÜ]]WÌÌÙ^\WØØXÚKH[YÜ]]WÌÌÝ\ÝÂ	ÝYÙYH
Ú]PÈ	ÛÜÈYKXØXÚYK[[YK[ÛHÚ\KSØXÝÈ	ÈX[	Ë[J
HHÜXXÚSØXÝÈ	Ë[J
HB
B	YÝYÙYH
	ÝYÙYÚ\KSØXÝÂ	È[H	Ø[YÜ]]WÌÌÙ^\WØØXÚKIÈX[	È[ÝZÙH	Ø[YÜ]]WÌÌÝ\ÝËÊÂB
BY
	YÝYÙYÛÝ[YÝ
HÂÝÈ[^XÝYÝYÙY[\Î	
	YÝYÙYZÚ[	Ë	ÊHB	Ú]PÈ	ÛÜÈXÈ\Ù\[YOIÔØ\Ú[ÛTÚ[XÚIÈXÈ\Ù\[XZ[IÌÎMMLLJÔØ\Ú[ÛTÚ[XÚP\Ù\ËÜ\KÚ]XÛÛIÈÛÛ[Z][H	Ð[YÜ]]HÌÌÞ[]XÈ][\IÂY
	TÕVUÓÑH[H
HÂÝÈÛÛ[Z]Z[YB	[Y]YXYH
Ú]PÈ	ÛÜÈ]\\ÙHPQ
K[J
BÜ]KRÜÝ[Y]YXY	[Y]YXYÜ]KRÜÝOOHL\XÝÚ]X[ÙOOHQÜYÜÝ[ÛÛÜÞX[	Ú]PÈ	ÛÜÈ\ÚÜYÚ[PQYËÚXYËÉ][\[ÚY
	TÕVUÓÑH[H
HÂÝÈÚ]X\ÚZ[YB	[[ÝS[HHÚ]PÈ	ÛÜÈË\[[ÝHÜYÚ[YËÚXYËÉ][\[ÚY
[Ý	[[ÝS[JHÂÝÈ[[ÝH[Ú\YXØ][ÛZ[YB	[[ÝRXYH
	[[ÝS[H\Ü]	×ÊÉÊVÌBY
	[[ÝRXY[H	[Y]YXY
HÂÝÈ[[ÝH[ÚÙ\ÈÝX]Ú[Y]YXYBÜ]KRÜÝOOHLK][ÝHY\][\HOOHQÜYÜÝ[ÛÛÜÞX[		YÞH\Ü][ÝHK[Ý]]YÜX]ÛÛÜ]KRÜÝOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOHQÜYÜÝ[ÛÛÜÜY[Ü]KRÜÝSQÔUUHÌÌUSTHÓÑSÈTÔÈQÜYÜÝ[ÛÛÜÜY[Ü]KRÜÝ[Ú	][\[ÚQÜYÜÝ[ÛÛÜÜY[Ü]KRÜÝXY	[Y]YXYQÜYÜÝ[ÛÛÜÜY[Ü]KRÜÝOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOHQÜYÜÝ[ÛÛÜÜY[BØ]ÚÂÜ]KRÜÝOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOHQÜYÜÝ[ÛÛÜYÜ]KRÜÝSQÔUUHÌÌUSTHÕÔQQÜYÜÝ[ÛÛÜYÜ]KRÜÝ	Ë^Ù\[ÛY\ÜØYÙHQÜYÜÝ[ÛÛÜYÜ]KRÜÝOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOHQÜYÜÝ[ÛÛÜYY
\ÝT]	ÛÜÊHÂÜ]KRÜÝ]Y[ÙHÛÜÜÜXÙH\Ù\Y]QÜYÜÝ[ÛÛÜY[ÝÂÜ]KRÜÝ	ÛÜÈQÜYÜÝ[ÛÛÜY[ÝÂBBB￿