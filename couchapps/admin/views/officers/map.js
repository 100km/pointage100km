function(doc) {
  // Categories and severities must be kept synchronous with Replicate.
  var categories = ["administrativia", "broadcast", "checkpoint", "connectivity", "race_info"];
  var severities = ["debug", "verbose", "info", "warning", "error", "critical"];
  if (doc.type == "officer" && doc.disabled != true) {
    var levels = doc.log_levels || {"*": "warning"}
    for (var i = 0; i < categories.length; i++) {
      var category = categories[i];
      var minlevel = levels[category] || levels["*"];
      if (minlevel != "none") {
        for (var s = Math.max(0, severities.indexOf(minlevel)); s < severities.length; s++) {
          emit([category, severities[s]], doc.officer);
        }
      }
    }
  }
}
