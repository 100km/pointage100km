# Databases

We will use CouchDB as our main database, in order to benefit from its
replication capabilities.

To limit the risk of incompatibilities, all databases should be running
the same version of CouchDB. For the moment, we have settled on 1.1.1.

## Database Setup

If we have `n` points of control (POC), we need at least `n+1` databases,
all connected around a central hub in a star configuration. Each
POC will have its own CouchDB instance, and will take care of setting up
continuous bidirectional replication with the central hub.

If we want to build services on top of the data, we should use other
databases that would replicate the central database in read-only mode.

## Manual Replication

To accomodate with connection losses, we need to be able to dump every
database onto an USB key, and reload the data from every other database
into the current one. This should not conflict with the built-in replication
mechanism in CouchDB, and will hopefully not need to be used.
