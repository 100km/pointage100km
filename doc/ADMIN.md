# Administration of the race

## Replicate

The `replicate` program has a mode where it can send alerts to /officers/ using various transports.
The officers must be configured in `steenwerck.conf`. By default, any officer not marked as `disabled = true`
in `steenwerck.conf` will receive messages with at least the `warning` severity level in every category.

### Configuration

A document for every officer in the database represents the chosen log-levels. It must look like:

```json
{
  "_id":        "officer-johndoe",
  "type":       "officer",
  "officer":    "johndoe",
  "log_levels": {
    "*":               "warning",   // Default log level for absent categories
    "race_info":       "info",
    "checkpoints":     "debug",
    "administrativia": "critical"   // Keep only the most critical admin info
  }
}
```

It is recommended that the log levels for the "system" officer (which logs to the application terminal) is
a single entry `{"*": "debug"}` as to keep a record of everything.

### Examination of sent messages

The alerts sent by `replicate` are stored into the database with ids starting with `alert-`. They contain
the content of the alert including the category and the severity, as well as the officers for which an
attempt delivery was made.
