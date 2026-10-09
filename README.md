# Repair Log

Request management with priorities, assignees, state transitions, expected revisions, and a persistent event history.

## Run

Requirements: Java 17.

```sh
javac RepairLog.java
java RepairLog reparos.log add centro 3 "Reparar luminária"
java RepairLog reparos.log report > result.json
```

## Behavior

Commands: `assign id revision operator`, `transition id revision state reason`, and `report`. States: OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, and CLOSED. Replay verifies the revision sequence and SHA-256 chain.

## Result synchronization

The [operations archive](https://vercel-home-telemetry-api.vercel.app/laboratory.html?project=java-neighborhood-repair-log) stores execution results. Supabase migrations are in the [API repository](https://github.com/brunnojob/vercel-home-telemetry-api/tree/main/supabase/migrations).

```sh
python cloud/sync.py enqueue result.json --project java-neighborhood-repair-log
python cloud/sync.py sync
```

Set `BRUNNODEV_ACCESS_TOKEN` to your session token. The SQLite outbox retains reports until the server confirms persistence; identical content does not create duplicate records. Tokens are not stored in source code. To run the synchronization tests:

```sh
python -m unittest discover -s cloud
```
