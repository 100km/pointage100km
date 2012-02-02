// View : recent-checkpoints
// Used to display the 50 last contestants recorded at a given site
function(doc) {
  for (var lap=0; lap<(doc.times && doc.times.length) || 0; lap++) {
    var res = {};
    res.lap = lap+1;
    res.bib = doc.bib;
    emit([doc.site_id, doc.times[lap]], res);
  }
};
