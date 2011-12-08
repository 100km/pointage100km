function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0) {
    var len = doc.times.length;
    emit([-len, doc.times[len-1]], doc);
  }
};
