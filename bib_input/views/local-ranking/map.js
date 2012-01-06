function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0 && doc.site_id != undefined && doc.race_id) {
    var len = doc.times.length;
    for (var i=0; i<len; i++) {
      emit([-doc.site_id, doc.race_id, -i-1, doc.times[i]], doc);
    }
  }
};
