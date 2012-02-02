// View : conflicting-checkpoints
// Used to know which documents conflict and resolv them automatically
function(doc) {
  if (doc.times && doc._conflicts) {
    emit(null, [doc._rev].concat(doc._conflicts));
  }
};
