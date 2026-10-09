# Repair Log

Gestão de solicitações com prioridades, responsáveis, transições de estado, revisão esperada e histórico de eventos persistente.

## Executar

Requisitos: Java 17.

```sh
javac RepairLog.java
java RepairLog reparos.log add centro 3 "Reparar luminária"
java RepairLog reparos.log report > resultado.json
```

## Funcionamento

Comandos: `assign id revisão operador`, `transition id revisão estado motivo` e `report`. Estados: OPEN, ASSIGNED, IN_PROGRESS, RESOLVED e CLOSED. O replay verifica sequência de revisões e encadeamento SHA-256.

## Persistência de resultados

O arquivo de operações está em [vercel-home-telemetry-api.vercel.app](https://vercel-home-telemetry-api.vercel.app/laboratory.html?project=java-neighborhood-repair-log). As migrações Supabase estão no [repositório da API](https://github.com/brunnojob/vercel-home-telemetry-api/tree/main/supabase/migrations).

```sh
python cloud/sync.py enqueue resultado.json --project java-neighborhood-repair-log
python cloud/sync.py sync
```

Defina `BRUNNODEV_ACCESS_TOKEN` com sua sessão. A fila SQLite conserva os relatórios até confirmação do servidor; o mesmo conteúdo não gera registros duplicados. Tokens não são gravados no código.
