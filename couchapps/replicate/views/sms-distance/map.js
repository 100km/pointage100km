function(doc) {
  if (doc.type === "sms")
    emit(doc.bib, doc.distance);
}
