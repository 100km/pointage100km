function(doc, req) {
  var command = JSON.parse(req.body);
  if (command === undefined)
    return [doc, { code: 400, body: "invalid body" }];
  var fields = ["bib", "race_id", "site_id", "timestamp"];
  for (var i = 0; i < fields.length; i++)
    if (!isFinite(command[fields[i]]))
      return [doc, { code: 400, body: "incorrect field " + fields[i] }];
  var ts = command.timestamp;

  // If the doc does not exist yet, create it with the basic information provided
  // by the caller.
  doc = doc || {
    _id: (req.id || req.uuid),
    type: "checkpoint",
    bib: command.bib,
    site_id: command.site_id,
    race_id: command.race_id,
  };

  // Do some sanity check on the _id.
  var expectedId = "checkpoints-" + command.site_id + "-" + command.bib;
  if (doc._id !== expectedId)
    return [null, { code: 400, body: "_id should be " + expectedId }];

  // Add fields that may be missing at this time.
  if (doc.times === undefined) doc.times = [];
  if (doc.deleted_times === undefined) doc.deleted_times = [];
  if (doc.artificial_times === undefined) doc.artificial_times = [];

  if (command.action === "add") {
    if (doc.times.indexOf(ts) === -1) {
      doc.times.push(ts);
      doc.times.sort();
    }
    var idx = doc.deleted_times.indexOf(ts);
    if (idx > -1)
      doc.deleted_times.splice(idx, 1);
    else if (doc.artificial_times.indexOf(ts) === -1) {
      doc.artificial_times.push(ts);
      doc.artificial_times.sort();
    }
  } else if (command.action === "remove") {
    var idx = doc.times.indexOf(ts);
    if (idx > -1) {
      doc.times.splice(idx, 1);
      idx = doc.artificial_times.indexOf(ts);
      if (idx > -1)
        doc.artificial_times.splice(idx, 1);
      else if (doc.deleted_times.indexOf(ts) === -1) {
        doc.deleted_times.push(ts);
        doc.deleted_times.sort();
      }
    }
  } else
    return [doc, { code: 400, body: "unknown or missing action" }];

  return [doc, ""];
}
