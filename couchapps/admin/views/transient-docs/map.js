function(doc) {
  if (doc.type == 'ping')
    emit(null, doc);
}
