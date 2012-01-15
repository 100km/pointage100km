function(doc) {
  if (doc.times && doc._conflicts) {
    emit(null, doc);
  }
};
