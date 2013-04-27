// View : global-ranking
// Used for global ranking of all contestants (to be diplayed in the web site for example)
//TODO teams
function(doc) {
  if (doc.bib != undefined && doc.times && doc.times.length > 0 && doc.site_id != undefined) {
    var len = doc.times.length;
    for (var i=0; i<len; i=i+1) {
      emit([doc.race_id, -(i+1), -doc.site_id, doc.times[i]], doc);
    }
  }
};
