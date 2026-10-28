# Reconciliation Engine

A command-line demo that reconciles DBS and Quantifeed records from two CSV files and writes the result to one CSV file.
It handles three record types: `POSITION` (holdings), `TRANSACTION` (trades) and `CASH` (balances).

Built with Java 21, Spring Boot 3.5 and Spring Batch. **No database or external service is needed.**

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
