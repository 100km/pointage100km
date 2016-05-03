function(doc) {
  if (doc.type === "contestant" && doc.stalkers && doc.stalkers.length > 0)
    emit(doc.bib, {bib: doc.bib, stalkers: doc.stalkers});
}
