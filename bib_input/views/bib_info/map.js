function(doc) {
  if (doc.dossard) {
    emit(doc.dossard, doc);
  }
};
