function(doc) {
  if (doc.bib && doc.times) {
    emit(doc.bib, doc);
  }
};
