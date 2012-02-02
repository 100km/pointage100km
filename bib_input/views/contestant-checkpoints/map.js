// View : contestant-checkpoints
// Used only for debug
function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0) {
    emit([doc.site_id, doc.bib], doc);
  }
};
