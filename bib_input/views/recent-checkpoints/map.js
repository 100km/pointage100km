// View : recent-checkpoints
// Used to display the 50 last contestants recorded at a given site
function(doc) {
  if (doc.bib) {
    for (var lap=0; lap<(doc.times && doc.times.length) || 0; lap++) {
      emit([doc.site_id, doc.times[lap]], {
        _id : "contestant-" + doc.bib,
        lap: lap+1,
        bib: doc.bib
      });
    }
  }
};
