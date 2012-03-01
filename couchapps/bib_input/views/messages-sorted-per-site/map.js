// View : all-messages
// Used to display the messages in bib_input
function(doc) {
  if (doc.message) {
    var active = doc.deletedTS == undefined;
    emit([doc.target, active, doc.addedTS], doc);
  }
};
