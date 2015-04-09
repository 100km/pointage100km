There exist several programs written in Scala (built using `make` at the top-level) that will help administer or test the setup:

- `loader` populates the database (incrementally) from the MySQL registration information.
- `replicate` initializes the database (by performing the initial
replication), allows to ensure that the database is continuously
replicated with the master, and helps solve conflicts and detect
inconsistencies in checkpoints information (for example when a contestant
information is not in the system yet).
- `stats` populates a database with some fake runners information.
- `wipe` selects a new database as the main database for a race. It does not delete data.

Before executing `stats` or the tests in `replicate` (using `./sbt
replicate/test`), the database must be put in test mode so that a running
race will not be accidentally polluted with dummy information. To do so,
the `test_allowed` field of the `configuration` document must be manually
set to `true`.

Newly created database will not be in test mode by default.
