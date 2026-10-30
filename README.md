# Reconciliation Engine

A command-line demo that reconciles DBS and Quantifeed records from two CSV files and writes the result to one CSV file.
Records are matched by `correlationId`, and each pair is checked in order: `POSITION` (security, quantity), then
`TRANSACTION` (side, price, amount, currency, settlement date), then `CASH` (balance). The first check that finds a
difference is reported as the break.

A trade can appear in one system before the other until it settles (e.g. T+2). So a record found in only one system
is `PENDING` (`AWAITING_SETTLEMENT`) while its `businessDate` + `settlement-days` is after the run date. After that it
is a `BREAK` (`MISSING_IN_DBS` or `MISSING_IN_QUANTIFEED`).

Both settings are in `application.yml`: `settlement-days` (default `2`) and `run-date` (default today). For example,
to reproduce the sample results on any day:

```bash
java -jar target/reconciliation-engine-0.1.0-SNAPSHOT.jar --reconciliation.run-date=2026-10-28
```

Built with Java 21, Spring Boot 3.5 and Spring Batch. **No external service is needed:** run history is kept in an
embedded H2 database file under `work/`, so a failed run can resume (see below).

## Build and run

Prerequisites: [SDKMAN](https://sdkman.io) and Maven 3.9+.

The Java version is pinned in `.sdkmanrc` (Java 21, Temurin). Run all commands from the project folder.

```bash
sdk env install   # first time only: install the Java version from .sdkmanrc
sdk env           # switch this shell to that Java version
mvn package
java -jar target/reconciliation-engine-0.1.0-SNAPSHOT.jar
```

Run `sdk env` in every new terminal before building or running. To switch automatically whenever you `cd` into the
project, set `sdkman_auto_env=true` in `~/.sdkman/etc/config`.

By default the app reads the sample files in `input/` and writes `output/reconciliation-results.csv`. To use other files:

```bash
java -jar target/reconciliation-engine-0.1.0-SNAPSHOT.jar \
  --reconciliation.input.dbs=/path/to/dbs.csv \
  --reconciliation.input.quantifeed=/path/to/quantifeed.csv \
  --reconciliation.output.file=/path/to/results.csv
```

The defaults are in `src/main/resources/application.yml`. Exit code `0` means the run completed (breaks are normal
results). `1` means it failed, for example on a missing file or missing columns.

## Retrying a failed step

Each step retries only the errors that can clear up by themselves: 3 attempts in total, 2 seconds apart. Any other
error fails the step at once.

| Step | Retried | Not retried (fails at once) |
|---|---|---|
| `validateInputStep` | input file not found or not readable (may still be arriving) | input path not configured |
| `normalizeInputStep` | I/O error reading a file | missing columns, malformed CSV |
| `matchStep` | nothing (works on data in memory) | e.g. duplicate `correlationId` |
| `compareStep` | nothing (works on data in memory) | any error |
| `exportStep` | I/O error writing the output (folder locked, disk full) | any other error |

Each retry is logged, for example `Retrying exportStep (attempt 2 of 3) after error: ...`. Change the attempts and delay
under `reconciliation.retry` in `application.yml`, or on the command line:

```bash
java -jar target/reconciliation-engine-0.1.0-SNAPSHOT.jar --reconciliation.retry.max-attempts=5 --reconciliation.retry.delay=10s
```

`max-attempts: 1` turns retry off. If every attempt fails, the run fails and the next start resumes it (see below).

## Resuming a failed run

If a run fails, the next start continues it from the step that failed. The steps that already completed are skipped,
and their output is read back from the run history. The resumed run keeps its `runId`, so the results file shows the
same `runId` as the failed attempt. For example, if writing the results fails because the output folder isn't
writable, fix the folder and start the app again: only the export step runs.

A fresh run starts instead when the input files, the run date or `settlement-days` differ from the failed run's. The
log shows the old and new settings. A different output file does not prevent a resume. When `run-date` is not set, a
run that failed on an earlier day therefore starts fresh.

To start fresh anyway, delete the `work/` folder. This clears the run history.

## Run in IntelliJ IDEA

IntelliJ doesn't read `.sdkmanrc`, so point it at the Java 21 that SDKMAN installed (run `sdk env install` once first).

1. **Open the project:** File → Open → select the project folder. IntelliJ detects `pom.xml` and imports it as a
   Maven project.
2. **Set Java 21:** File → Project Structure → Project → SDK → Add SDK → JDK… and choose
   `~/.sdkman/candidates/java/21.0.13.0+9-tem`. (In the file picker, press `Cmd+Shift+.` to show hidden folders.)
   Set **Language level** to 21.
3. **Point Maven at Java 21:** Settings → Build, Execution, Deployment → Build Tools → Maven:
   - **Runner → JRE:** the Java 21 SDK from step 2
   - **Importing → JDK for importer:** the same SDK

   Then click **Reload All Maven Projects** in the Maven tool window.
4. **Run:** open `src/main/java/com/example/reconciliation/ReconciliationApplication.java` and click the green ▶ next
   to `main`. The output goes to `output/reconciliation-results.csv`.

To use other files, add them under Run → Edit Configurations → **Program arguments**:

```text
--reconciliation.input.dbs=/path/to/dbs.csv --reconciliation.input.quantifeed=/path/to/quantifeed.csv
```

**Troubleshooting**

- *"input file not found":* in Run → Edit Configurations, set **Working directory** to the project folder.
- *Dependency downloads fail with a login error:* the project downloads from public Maven Central through
  `.mvn/settings.xml`. If IntelliJ uses your global `~/.m2/settings.xml` instead, go to Settings → Build Tools → Maven,
  tick **Override** next to **User settings file**, and select `.mvn/settings.xml` in the project folder.
