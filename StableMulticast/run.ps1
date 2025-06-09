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

# --- INÍCIO DA SOLUÇÃO COM SPLATTING ---

# Pega os caminhos completos dos arquivos .java como um array de strings
$StableMulticastSourceFilesArray = Get-ChildItem -Path "$SRC_DIR\$STABLE_MULTICAST_PACKAGE" -Filter "*.java" | Select-Object -ExpandProperty FullName

# **ADICIONAL: Verificação de arquivos encontrados** (boa prática para depuração)
if (-not $StableMulticastSourceFilesArray) {
    Write-Host "Error: No .java source files found in $SRC_DIR\$STABLE_MULTICAST_PACKAGE. Check your path and file existence." -ForegroundColor Red
    exit 1
}

# Cria um array para os argumentos de javac, incluindo -d e o diretório de saída
$javacArgs = @("-d", "$BIN_DIR") + $StableMulticastSourceFilesArray

# Usa o operador de splatting '@' para passar os argumentos para javac
javac @javacArgs

# --- FIM DA SOLUÇÃO COM SPLATTING ---


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
Write-Host "Example: java -cp $BIN_DIR $MAIN_APP P1 127.0.0.1 5000"
Write-Host "Example: java -cp $BIN_DIR $MAIN_APP P2 127.0.0.1 5001"
Write-Host "Example: java -cp $BIN_DIR $MAIN_APP P3 127.0.0.1 5002"