// View : all-messages
// Used to send alerts in replicate
function(doc) {
  if (doc.message) {
    emit(doc.deletedTS || doc.addedTS, doc);
  }
};
