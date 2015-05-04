function(doc) {
  if (doc.type == "alert")
    emit(-doc.addedTS, doc);
}
