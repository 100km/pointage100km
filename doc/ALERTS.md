Officers are people who receive alerts from `replicate master`. They are defined in the `steenwerck.conf`
configuration file with their contact characteristics. Alert levels can be adjusted using documents
`officer-NNN` where `NNN` is the officer name. Those documents are created at `replicate master` startup
if they don't exist. They are also copied by `wipe` from the configuration database if they are present
to allow different defaults for some officers.

By default, an officer will receive alerts starting at `Warning` severity for every category.
The list of categories is:

- `Administrativia`: information about `replicate` itself (such as the program starting);
- `Broadcast`: about messages sent to checkpoint sites ("Yeah contestants" for example);
- `Checkpoint`: information about checkpoints liveness, and analysis storage problems;
- `Connectivity`: unused so far;
- `RaceInfo`: information about new best runners or suspicious rank changes;
- `TextMessage`: SMS system.

The severities, in order, are:

- `Debug`: debugging information, do not use in production;
- `Verbose`: useless but entertaining information;
- `Info`: useful information to have;
- `Warning`: information which might need to be acted upon;
- `Error`: problem in the system;
- `Critical`: some subsystem might not be functioning.
