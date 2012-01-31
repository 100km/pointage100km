function(doc) {
  if (doc.times && doc._conflicts) {
    emit(null, [doc._rev].concat(doc._conflicts));
  }
};
