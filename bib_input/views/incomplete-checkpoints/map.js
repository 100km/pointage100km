function(doc) {
  if (doc.times && doc.race_id == 0) {
    emit(null, doc);
  }
};
