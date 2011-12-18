function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0 && doc.site_id != undefined) {
    var len = doc.times.length;
    emit([-doc.site_id, -len, doc.times[len-1]], doc);
  }
};
