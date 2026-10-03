# java-neighborhood-repair-log

Java 17 local maintenance request log with generated IDs, timestamps, status and area. Records append to a tab-separated file without requiring a database.

Compile: `javac RepairLog.java`. Add: `java RepairLog repairs.tsv add kitchen "leaking tap"`. List: `java RepairLog repairs.tsv list`.

Project by [Brunno Dev](https://brunnodev.store).