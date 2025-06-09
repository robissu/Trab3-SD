# run.ps1

# Define paths
$SRC_DIR="src"
$BIN_DIR="bin"
$STABLE_MULTICAST_PACKAGE="StableMulticast"
$MAIN_APP="MyApplication"

Write-Host "Cleaning up..."
Remove-Item -Recurse -Force "$BIN_DIR" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "$BIN_DIR" -Force

Write-Host "Compiling StableMulticast package..."

# Encontra todos os arquivos .java no diretório do pacote e os formata como uma string separada por espaço
$StableMulticastSourceFiles = Get-ChildItem -Path "$SRC_DIR\$STABLE_MULTICAST_PACKAGE" -Filter "*.java" | ForEach-Object { $_.FullName } | Sort-Object | Out-String -Stream | ForEach-Object { $_.Trim() } | Where-Object { $_ } # Limpa e garante que seja uma única string para javac
$StableMulticastSourceFiles = $StableMulticastSourceFiles -join " " # Garante que os caminhos sejam separados por espaço

# Agora passe a string dos arquivos para javac
javac -d "$BIN_DIR" $StableMulticastSourceFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of StableMulticast failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compiling user application..."
javac -d "$BIN_DIR" -cp "$BIN_DIR" "$SRC_DIR\$MAIN_APP.java"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of user application failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful. Now you can run instances:" -ForegroundColor Green
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P1 127.0.0.1 5000"
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P2 127.0.0.1 5001"
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P3 127.0.0.1 5002"