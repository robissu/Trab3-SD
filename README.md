# Projeto StableMulticast

Este projeto implementa um middleware de comunicação multicast estável, utilizando relógios vetoriais de vetores (vector clock of vector clocks) para garantir a estabilidade das mensagens em um ambiente distribuído. Ele simula um ambiente de sistema distribuído onde múltiplas instâncias da aplicação podem se comunicar, mantendo a consistência de mensagens através de um algoritmo de estabilização.

## Estrutura do Projeto

A estrutura do projeto é organizada da seguinte forma:

Com certeza! Criar um README.md é uma excelente prática para qualquer projeto, facilitando muito a vida de quem precisa compilar e executar.

Aqui está um README.md completo com todas as orientações que discutimos, formatado em Markdown para fácil visualização em repositórios como o GitHub.

Markdown

# Projeto StableMulticast

Este projeto implementa um middleware de comunicação multicast estável, utilizando relógios vetoriais de vetores (vector clock of vector clocks) para garantir a estabilidade das mensagens em um ambiente distribuído. Ele simula um ambiente de sistema distribuído onde múltiplas instâncias da aplicação podem se comunicar, mantendo a consistência de mensagens através de um algoritmo de estabilização.

## Requisitos

Para compilar e executar este projeto, você precisará ter o seguinte instalado:

* **Java Development Kit (JDK) 8 ou superior:** Certifique-se de que `javac` e `java` estejam disponíveis em seu PATH.
* **PowerShell (no Windows):** Para executar o script `run.ps1`.
* **Git Bash / WSL / Terminal Linux/macOS (opcional, para execução em ambientes Unix-like):** Para comandos de execução que usam barras normais no classpath.

## Compilação do Projeto

O projeto pode ser compilado utilizando o script `run.ps1` fornecido. Este script automatiza o processo de limpeza, compilação do middleware e da aplicação exemplo.

1.  **Abra o Terminal:**
    * No Windows: Abra o **PowerShell**.
    * No macOS/Linux/WSL: Abra seu terminal Bash/Zsh/etc.

2.  **Navegue até o diretório raiz do projeto:**
    Certifique-se de estar no diretório `trab sd3` (onde o `run.ps1` e a pasta `StableMulticast` estão).

    ```bash
    cd /caminho/para/o/seu/trab sd3
    ```
    (Ex: `cd C:\Users\SeuUsuario\Desktop\trab sd3` no Windows)

3.  **Execute o script de compilação:**

    ```powershell
    .\run.ps1
    ```

    Este script irá:
    * Remover a pasta `StableMulticast/bin` (se existir).
    * Recriar a pasta `StableMulticast/bin`.
    * Compilar todos os arquivos `.java` do pacote `StableMulticast` para `StableMulticast/bin`.
    * Compilar `MyApplication.java` para `StableMulticast/bin`.
    * Exibir mensagens de sucesso ou erro.

## Execução da Aplicação

Após a compilação bem-sucedida, você pode executar múltiplas instâncias da `MyApplication` para testar a comunicação e a estabilização. Cada instância deve ser executada em um terminal separado.

**Importante:** Sempre execute os comandos `java` do diretório `trab sd3`.

### Para Windows (PowerShell/CMD)

Use a barra invertida (`\`) nos caminhos do classpath.

```bash
# Instância 1 (Exemplo)
java -cp StableMulticast\bin MyApplication P 127.0.0.1 5000

# Instância 2 (Exemplo)
java -cp StableMulticast\bin MyApplication P 127.0.0.1 5001

# Instância 3 (Exemplo)
java -cp StableMulticast\bin MyApplication P 127.0.0.1 5002
Para macOS / Linux / WSL (Bash/Zsh/etc.)
Use a barra normal (/) nos caminhos do classpath.

Bash

# Instância 1 (Exemplo)
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5000

# Instância 2 (Exemplo)
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5001

# Instância 3 (Exemplo)
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5002
Parâmetros de Execução:

<namePrefix>: Um prefixo de texto para o nome da instância (ex: P, Node, Client). O ID numérico real (0, 1, 2...) será atribuído dinamicamente pelo middleware e anexado a este prefixo na saída do console.
<ip>: O endereço IP da interface de rede a ser utilizada (geralmente 127.0.0.1 para testes locais).
<port>: A porta UDP para esta instância se comunicar (deve ser diferente para cada instância que rodar na mesma máquina).
Testando a Comunicação e Estabilização
Abra vários terminais e execute uma instância da MyApplication em cada um, com portas diferentes.
Observe as mensagens de "Group members updated" e "Resizing MulticastClock", indicando a descoberta dinâmica.
Digite mensagens em um dos terminais e observe:
O incremento do MulticastClock da instância remetente.
As mensagens de "Sent unicast message".
As mensagens de "Received" e "DELIVERED" nas outras instâncias.
As atualizações do MulticastClock nas instâncias receptoras.
O "Message Buffer" sendo preenchido e, crucialmente, as mensagens "Discarding stable message" quando a condição de estabilidade é atendida.
Experimente fechar um terminal abruptamente e observe como as instâncias restantes detectam a mudança e ajustam seus MulticastClocks.
