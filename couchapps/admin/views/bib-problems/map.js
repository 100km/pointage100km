// View : bib-problems
// This view assemble bib_info and contestant-checkpoints
// Used to get all the info about the bib in order to detect problems
function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0) {
    emit([doc.bib,doc.site_id], doc);
  }
};
