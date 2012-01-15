function(doc) {
  if (doc.dossard != undefined) {
    emit(doc.dossard, doc);
  }
};
