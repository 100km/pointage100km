function(doc, req) {
  // Those are already strings, keep them as strings
  var site_id = req.query.site_id;
  var prev_site_id = req.query.prev_site_id;

  var startsWith = function(prefix) {
    return doc._id.substring(0, prefix.length) === prefix;
  };

  if (doc.scope === "local")
    return false;

  return doc._id === "infos" ||
    doc._id === "configuration" ||
    doc._id === "couchsync" ||
    (site_id !== undefined && startsWith("checkpoints-" + site_id + "-")) ||
    (prev_site_id !== undefined && startsWith("checkpoints-" + prev_site_id + "-")) ||
    startsWith("contestant-") ||
    startsWith("_design/") ||
    startsWith("message-") ||

    // Analysis documents are needed on site 6 for the main display.
    (startsWith("analysis-") && site_id == 6);
}
