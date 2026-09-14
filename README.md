# StableMulticast — middleware de multicast estável

Middleware em Java para comunicação multicast com **estabilidade de mensagens** em um sistema
distribuído. Cada processo mantém um **relógio vetorial de vetores** (*vector clock of vector clocks*):
além do próprio vetor, guarda a última visão que tem do vetor de cada outro processo. Com isso é
possível determinar quando uma mensagem já foi recebida por todos e pode ser descartada do buffer
— a **estabilização**.

Trabalho da disciplina de **Sistemas Distribuídos**, Ciência da Computação, UFSM (2025).

## Estrutura

```
StableMulticast/src/
├── MyApplication.java                    # aplicação de exemplo que usa o middleware
└── StableMulticast/
    ├── IStableMulticast.java             # interface de entrega para a aplicação
    ├── StableMulticast.java              # o middleware: envio, recepção, buffer e estabilização
    ├── StableMulticastMessage.java       # mensagem com o relógio anexado
    └── MulticastClock.java               # relógio vetorial de vetores
run.ps1                                   # compila tudo para StableMulticast/bin
```

## Como executar

Requer JDK 8+.

```powershell
.\run.ps1
```

Depois, uma instância por terminal, todas a partir da raiz do projeto:

```bash
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5000
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5001
java -cp StableMulticast/bin MyApplication P 127.0.0.1 5002
```

No Windows, use `StableMulticast\bin` no classpath.

## Estado do projeto

Entrega de disciplina. O mecanismo de estabilização por relógio vetorial de vetores está
implementado; a **ordenação causal** não ficou completa em todos os cenários testados.
