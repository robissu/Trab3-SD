# run.ps1 (Este arquivo agora está em 'trab sd3/')

# Define a pasta raiz do seu projeto (que contém StableMulticast/)
# Como run.ps1 está em 'trab sd3', o caminho relativo para StableMulticast é 'StableMulticast'
$PROJECT_ROOT_DIR = "StableMulticast"

# Define paths relativos à $PROJECT_ROOT_DIR
$SRC_DIR = "$PROJECT_ROOT_DIR\src"
$BIN_DIR = "$PROJECT_ROOT_DIR\bin"
$STABLE_MULTICAST_PACKAGE = "StableMulticast" # Este continua sendo o nome do pacote Java
$MAIN_APP = "MyApplication" # O nome da sua classe principal sem .java

Write-Host "Cleaning up..."
# Remove recursivamente o diretório bin dentro de StableMulticast.
# O caminho completo agora é "$BIN_DIR" (ex: "StableMulticast\bin").
Remove-Item -Recurse -Force "$BIN_DIR" -ErrorAction SilentlyContinue
# Cria o diretório bin dentro de StableMulticast.
New-Item -ItemType Directory -Path "$BIN_DIR" -Force

Write-Host "Compiling StableMulticast package..."

# Pega os caminhos completos dos arquivos .java dentro de StableMulticast/src/StableMulticast/
# O caminho para Get-ChildItem agora é "$SRC_DIR\$STABLE_MULTICAST_PACKAGE"
$StableMulticastSourceFilesArray = Get-ChildItem -Path "$SRC_DIR\$STABLE_MULTICAST_PACKAGE" -Filter "*.java" | Select-Object -ExpandProperty FullName

# Verificação de arquivos encontrados
if (-not $StableMulticastSourceFilesArray) {
    Write-Host "Error: No .java source files found in $SRC_DIR\$STABLE_MULTICAST_PACKAGE. Check your path and file existence." -ForegroundColor Red
    exit 1
}

# Cria um array para os argumentos de javac
$javacArgs = @("-d", "$BIN_DIR") + $StableMulticastSourceFilesArray

# Usa o operador de splatting '@' para passar os argumentos
javac @javacArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of StableMulticast failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compiling user application..."
# Compila MyApplication.java, que está em StableMulticast/src/MyApplication.java
# O classpath para execução da aplicação precisa apontar para a pasta 'bin' dentro de StableMulticast.
# O caminho completo para o arquivo Myappication.java é "$SRC_DIR\$MAIN_APP.java"
javac -d "$BIN_DIR" -cp "$BIN_DIR" "$SRC_DIR\$MAIN_APP.java"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of user application failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful." -ForegroundColor Green
Write-Host "--------------------------------------------------------"
Write-Host "To RUN the application (open multiple terminals):"
Write-Host "--------------------------------------------------------"

Write-Host "For Windows PowerShell/CMD:"
Write-Host "  java -cp StableMulticast\bin MyApplication P 127.0.0.1 5000"
Write-Host "  java -cp StableMulticast\bin MyApplication P 127.0.0.1 5001"
Write-Host "  ..."
Write-Host "  (Note: '\' works in Windows for java -cp when cmd/powershell processes it)"

Write-Host "`nFor macOS/Linux/WSL (Bash/Zsh/etc.):"
Write-Host "  java -cp StableMulticast/bin MyApplication P 127.0.0.1 5000"
Write-Host "  java -cp StableMulticast/bin MyApplication P 127.0.0.1 5001"
Write-Host "  ..."
Write-Host "  (Note: '/' is the standard path separator)"

Write-Host "`nImportant considerations:"
Write-Host "- Always run these commands from the 'trab sd3' directory (where this run.ps1 is)."
Write-Host "- For 'P' in 'MyApplication P', you can use any prefix like 'Client', 'Node', etc."
Write-Host "  The actual ID (0, 1, 2...) will be assigned by the middleware."
Write-Host "--------------------------------------------------------"