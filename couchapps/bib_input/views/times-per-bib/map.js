// View : times-per-bib
// Show all the times for each bib. Let us know the last known checkpoint (for the average).
//TODO teams
function(doc) {
  for (var lap=0; lap<(doc.times && doc.times.length) || 0; lap++) {
    emit([doc.bib, doc.times[lap]], [doc.site_id, lap+1]);
  }
};
