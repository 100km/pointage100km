function() {
  var site_id = $(this).attr('data-site_id');
  var lap = $(this).attr('data-lap');
  var bib = $(this).attr('data-bib');
  var db = $$(this).app.db;

  db.openDoc('checkpoints-' + site_id + '-' + bib, {
    success: function(doc) {
      // Remove the time from the deleted_times array.
      var deleted_time = doc.deleted_times[lap];
      doc.deleted_times = $.grep(doc.deleted_times, function(element) { return element != deleted_time; });
      doc.times = doc.times || [];
      if (isTimestampValid(deleted_time)) {
        doc.times.push(deleted_time);
      } else {
        alert ("Instead of restore, dropped invalid timestamp" + deleted_time);
      }
      doc.times.sort();
      // Save the document.
      db.saveDoc(doc);
      $('#bib_admin_problems').trigger('_init');
    }
  });
}
