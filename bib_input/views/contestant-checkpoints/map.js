function(doc) {
  if (doc.type == "contestant-checkpoint") {
    emit(doc.bib, doc);
  }
};
