// View : all-times-per-bib
// Used to display the times of a contestant (search)
//TODO teams
function(doc) {
  if (doc.bib != undefined && doc.times && doc.site_id != undefined) {
    var len = doc.times.length;
    for (var i=0; i<len; i=i+1) {
      emit([doc.bib, i+1, doc.site_id], doc.times[i]);
    }
  }
};
