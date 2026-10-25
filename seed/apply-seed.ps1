<#
  Nạp seed dev vào PostgreSQL bằng psql chạy trong Docker.

  Thông tin kết nối lấy từ backend/.env (DB_URL, DB_USERNAME, DB_PASSWORD), nên script dùng
  được cho cả Postgres local của Docker Compose lẫn Supabase/Neon. Mật khẩu không bao giờ được
  in ra màn hình hay truyền trên dòng lệnh: script ghi tạm vào một file env rồi xoá.

  Cách dùng (chạy trong thư mục backend/):
    .\seed\apply-seed.ps1                            # hỏi xác nhận khi DB không phải localhost
    .\seed\apply-seed.ps1 -Force                     # bỏ qua câu hỏi xác nhận
    .\seed\apply-seed.ps1 -SqlFile seed\seed-dev.sql
    .\seed\apply-seed.ps1 -EnvFile .env.supabase      # dùng file cấu hình khác

  Lưu ý: file này phải lưu ở dạng UTF-8 CÓ BOM để Windows PowerShell 5.1 đọc đúng tiếng Việt.
#>
[CmdletBinding()]
param(
  [string]$EnvFile,
  [string]$SqlFile,
  [string]$Image = 'pgvector/pgvector:pg16',
  [switch]$Force
)

$ErrorActionPreference = 'Stop'

# Console Windows mặc định không dùng UTF-8, tiếng Việt trong Write-Host sẽ bị lỗi font.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

# $PSScriptRoot không đáng tin trong default của param ở Windows PowerShell 5.1,
# nên resolve đường dẫn mặc định ngay sau khi script bắt đầu chạy.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $EnvFile) { $EnvFile = Join-Path $scriptDir '..\.env' }
if (-not $SqlFile) { $SqlFile = Join-Path $scriptDir 'seed-dev.sql' }

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Không tìm thấy file cấu hình: $EnvFile" }
if (-not (Test-Path -LiteralPath $SqlFile)) { throw "Không tìm thấy file SQL: $SqlFile" }

# --- Đọc .env ở dạng KEY=value (không hỗ trợ dấu ngoặc kép, export, ký tự # trong giá trị) ---
$cfg = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
  $trimmed = $line.Trim()
  if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
  $parts = $trimmed -split '=', 2
  if ($parts.Count -eq 2) { $cfg[$parts[0].Trim()] = $parts[1].Trim() }
}

foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
  if (-not $cfg[$key]) { throw "Thiếu $key trong $EnvFile" }
}

# --- Tách JDBC URL: jdbc:postgresql://host[:port]/database[?params] ---
if ($cfg['DB_URL'] -notmatch '^jdbc:postgresql://(?<rest>.+)$') {
  throw "DB_URL không phải JDBC URL PostgreSQL: $($cfg['DB_URL'])"
}
$rest = $Matches['rest']
$hostPart, $afterSlash = $rest -split '/', 2
$dbAndQuery = $afterSlash
$query = ''
if ($afterSlash -match '\?(?<q>.+)$') {
  $dbAndQuery = $afterSlash.Substring(0, $afterSlash.IndexOf('?'))
  $query = $Matches['q']
}
if ($hostPart -match '^(?<h>.+):(?<p>\d+)$') { $dbHost = $Matches['h']; $dbPort = $Matches['p'] }
else { $dbHost = $hostPart; $dbPort = '5432' }
$dbName = $dbAndQuery
if (-not $dbName) { throw "DB_URL thiếu tên database: $($cfg['DB_URL'])" }
$sslMode = ''
if ($query -match 'sslmode=(?<m>[^&]+)') { $sslMode = $Matches['m'] }

# psql chạy trong container: localhost của máy host phải gọi bằng host.docker.internal.
$isLocal = $dbHost -in @('localhost', '127.0.0.1', '::1')
if ($isLocal) { $dbHost = 'host.docker.internal' }

Write-Host "Nguồn SQL : $SqlFile"
Write-Host "Đích      : $($cfg['DB_USERNAME'])@$dbHost`:$dbPort/$dbName (sslmode=$sslMode)"
if (-not $isLocal -and -not $Force) {
  $answer = Read-Host 'Database không phải localhost. Tiếp tục nạp? (y/N)'
  if ($answer -notin @('y', 'Y', 'yes', 'YES')) { Write-Host 'Đã huỷ.'; return }
}

# --- File env tạm chứa mật khẩu (xoá trong finally) ---
$tempEnv = [System.IO.Path]::GetTempFileName()
try {
  $envContent = "PGPASSWORD=$($cfg['DB_PASSWORD'])`n"
  if ($sslMode) { $envContent += "PGSSLMODE=$sslMode`n" }
  # client_min_messages=warning: ẩn các NOTICE (ví dụ pgcrypto đã tồn tại) cho output gọn.
  $envContent += "PGOPTIONS=-c client_min_messages=warning`n"
  [System.IO.File]::WriteAllText($tempEnv, $envContent, (New-Object System.Text.UTF8Encoding $false))

  $sqlDir = Split-Path -Parent (Resolve-Path -LiteralPath $SqlFile)
  $sqlName = Split-Path -Leaf $SqlFile
  $summarySql = "SELECT role, count(*) AS accounts FROM users GROUP BY role ORDER BY role; " +
    "SELECT c.name AS category, count(*) AS products FROM products p " +
    "JOIN categories c ON c.id = p.category_id GROUP BY c.name ORDER BY c.name;"

  # Windows PowerShell 5.1 biến mỗi dòng stderr của lệnh ngoài thành ErrorRecord, mà
  # $ErrorActionPreference = 'Stop' sẽ biến nó thành lỗi terminating. Vì vậy hạ tạm
  # ErrorActionPreference khi gọi docker rồi tự kiểm tra exit code.
  $previousEap = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $output = docker run --rm --env-file $tempEnv -v "${sqlDir}:/seed:ro" $Image `
      psql -h $dbHost -p $dbPort -U $cfg['DB_USERNAME'] -d $dbName -v ON_ERROR_STOP=1 `
      -f "/seed/$sqlName" -c $summarySql 2>&1
    $dockerExit = $LASTEXITCODE
  }
  finally {
    $ErrorActionPreference = $previousEap
  }

  $output | ForEach-Object { Write-Host "$_" }
  if ($dockerExit -ne 0) { throw "psql thất bại (exit code $dockerExit)" }
  Write-Host 'Nạp seed xong.'
}
finally {
  Remove-Item -LiteralPath $tempEnv -Force -ErrorAction SilentlyContinue
  if (Test-Path -LiteralPath $tempEnv) {
    Write-Warning "Không xoá được file env tạm, hãy xoá tay: $tempEnv"
  }
}
