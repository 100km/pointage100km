// View incomplete-checkpoints
// Used in scala in order to fix contectant with race 0
function(doc) {
  if (doc.times && doc.times.length > 0 && doc.race_id == 0) {
    emit(null, doc);
  }
};
