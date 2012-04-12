function() {
  var site_id = $(this).attr('data-site_id');
  var lap = $(this).attr('data-lap');
  var bib = $(this).attr('data-bib');
  var db = $$(this).app.db;

  db.openDoc('checkpoints-' + site_id + '-' + bib, {
    success: function(doc) {
      doc.times = $.grep(doc.times, function(element, index) { return index != lap; });
      db.saveDoc(doc);
    }
  });
}
