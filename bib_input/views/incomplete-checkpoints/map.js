function(doc) {
  if (doc.times && doc.times.length > 0 && doc.race_id == 0) {
    emit(null, doc);
  }
};
