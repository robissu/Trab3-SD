# run.ps1 (Você pode salvar isso como run.ps1)

# Define paths
$SRC_DIR="src"
$BIN_DIR="bin"
$STABLE_MULTICAST_PACKAGE="StableMulticast"
$MAIN_APP="MyApplication" # O nome da sua classe principal sem .java

Write-Host "Cleaning up..."
# Remove recursivamente o diretório bin. -ErrorAction SilentlyContinue evita que erros sejam exibidos se o diretório não existir.
Remove-Item -Recurse -Force "$BIN_DIR" -ErrorAction SilentlyContinue
# Cria o diretório bin
New-Item -ItemType Directory -Path "$BIN_DIR" -Force

Write-Host "Compiling StableMulticast package..."
# Compila todos os arquivos Java dentro do pacote StableMulticast
# Note que no PowerShell/CMD, o caractere curinga (*) no caminho funciona de forma diferente ou não diretamente como no Bash para javac.
# É mais seguro listar a pasta ou usar um loop se houver muitos arquivos em subpastas.
# Para este caso específico, "StableMulticast\*.java" funciona.
javac -d "$BIN_DIR" "$SRC_DIR\$STABLE_MULTICAST_PACKAGE\*.java"

# Verifica o código de saída do comando anterior. $LASTEXITCODE é o equivalente a $? no Bash.
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of StableMulticast failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compiling user application..."
# Compila a aplicação do usuário, especificando o classpath para encontrar as classes compiladas do StableMulticast.
javac -d "$BIN_DIR" -cp "$BIN_DIR" "$SRC_DIR\$MAIN_APP.java"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation of user application failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful. Now you can run instances:" -ForegroundColor Green
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P1 127.0.0.1 5000"
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P2 127.0.0.1 5001"
Write-Host "Example: java -cp $BIN_DIR $STABLE_MULTICAST_PACKAGE.$MAIN_APP P3 127.0.0.1 5002"

# Nota: Para executar as instâncias, você precisará abrir terminais separados
# e digitar os comandos "java -cp bin/ StableMulticast.MyApplication ..." manualmente.
# O PowerShell pode executar múltiplos processos em background, mas o controle interativo
# que você deseja para depuração (digitar mensagens) exige terminais separados.