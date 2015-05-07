function (doc) {
  if (doc.type == "contestant" && doc.stalkers.length != 0)
    emit(doc.bib, doc);
}
