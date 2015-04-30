// View : all-messages
// Used to send alerts in replicate
function(doc) {
  var ts = doc.deletedTS || doc.addedTS;
  if (doc.message && ts) {
    emit(ts, doc);
  }
};
