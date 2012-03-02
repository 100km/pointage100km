function(doc) {
  if (doc.type == 'ping' || doc.type == 'touch')
    emit(null, doc);
}
