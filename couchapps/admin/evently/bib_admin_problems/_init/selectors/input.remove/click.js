function() {
  var site_id = $(this).attr('data-site_id');
  var lap = $(this).attr('data-lap');
  var bib = $(this).attr('data-bib');
  var db = $$(this).app.db;

  db.openDoc('checkpoints-' + site_id + '-' + bib, {
    success: function(doc) {
      // Put the time in the deleted_times array.
      var time = doc.times[lap];
      doc.times = $.grep(doc.times, function(element) { return element != time; });
      doc.deleted_times = doc.deleted_times || [];
      doc.deleted_times.push(time);
      // Save the document.
      db.saveDoc(doc);
      $('#bib_admin_problems').trigger('_init');
    }
  });
}
