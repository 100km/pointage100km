function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0) {
    emit(doc.bib, doc);
  }
};
