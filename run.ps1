# run.ps1 (Este arquivo agora estará na pasta 'trab sd3')

# Define paths (agora relativos a 'trab sd3')
# Todas as referências a 'src' e 'bin' precisam incluir 'StableMulticast/'
$PROJECT_DIR="StableMulticast" # Adiciona uma variável para a pasta do projeto
$SRC_DIR="$PROJECT_DIR\src"
$BIN_DIR="$PROJECT_DIR\bin"
$STABLE_MULTICAST_PACKAGE="StableMulticast"
$MAIN_APP="MyApplication"

Write-Host "Cleaning up project: $PROJECT_DIR..."
# Remove recursivamente o diretório bin. -ErrorAction SilentlyContinue evita que erros sejam exibidos se o diretório não existir.
Remove-Item -Recurse -Force "$BIN_DIR" -ErrorAction SilentlyContinue
# Cria o diretório bin
New-Item -ItemType Directory -Path "$BIN_DIR" -Force

Write-Host "Compiling StableMulticast package..."

# Pega os caminhos completos dos arquivos .java como um array de strings
# O caminho para Get-ChildItem também precisa ser ajustado
$StableMulticastSourceFilesArray = Get-ChildItem -Path "$SRC_DIR\$STABLE_MULTICAST_PACKAGE" -Filter "*.java" | Select-Object -ExpandProperty FullName

# Verificação de arquivos encontrados
if (-not $StableMulticastSourceFilesArray) {
    Write-Host "Error: No .java source files found in $SRC_DIR\$STABLE_MULTICAST_PACKAGE. Check your path and file existence." -ForegroundColor Red
    exit 1
}

# Cria um array para os argumentos de javac
$javacArgs = @("-d", "$BIN_DIR") + $StableMulticastSourceFilesArray

# Usa o operador de splatting '@' para passar os argumentos para javac
javac @javacArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of StableMulticast failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compiling user application..."
# O caminho para MyApplication.java também precisa ser ajustado
javac -d "$BIN_DIR" -cp "$BIN_DIR" "$SRC_DIR\$MAIN_APP.java"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of user application failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful. Now you can run instances:" -ForegroundColor Green
# As instruções de execução também precisam refletir a nova localização do bin
Write-Host "Navigate to the project directory first: cd $PROJECT_DIR"
Write-Host "Example: java -cp bin $MAIN_APP P 127.0.0.1 5000"
Write-Host "Example: java -cp bin $MAIN_APP P 127.0.0.1 5001"
Write-Host "Example: java -cp bin $MAIN_APP P 127.0.0.1 5002"
Write-Host "Or, if running from this directory: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P 127.0.0.1 5000"
Write-Host "Note: For MyApplication, use 'P' as the first argument, not 'P1', to display P0, P1, etc."