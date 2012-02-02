// View : global-ranking
// Used for global ranking of all contestants (to be diplayed in the web site for example)
function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0 && doc.site_id != undefined) {
    var len = doc.times.length;
      emit([doc.race_id, -len, -doc.site_id, doc.times[len-1]], doc);
  }
};
