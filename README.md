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

## Optional report archive

Export a JSON report from the command above, then run `python cloud/sync.py enqueue result.json --project java-neighborhood-repair-log` and `python cloud/sync.py sync`. Synchronization requires `BRUNNODEV_ACCESS_TOKEN` and the external operations API; the local outbox retains unacknowledged reports.

## License

Original source and documentation are MIT licensed; see [LICENSE](LICENSE). Third-party dependencies and media retain their respective terms. Maintained by [Brunno Dev](https://brunnodev.store).
