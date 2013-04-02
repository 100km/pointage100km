// View : local-rank
// Used for previous info : this let us know the rank of contestant X at a given site-id for a given lap !
//TODO teams
function(doc) {
  if (doc.bib != undefined && doc.times && doc.times.length > 0 && doc.site_id != undefined && doc.race_id != undefined) {
    var len = doc.times.length;
    for (var i=0; i<len; i++) {
      emit([-doc.site_id, doc.race_id, -i-1, doc.times[i]], 1);
    }
  }
};
