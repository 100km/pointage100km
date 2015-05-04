// View : all-valid-messages
// Used to display the messages in bib_admin_message_list_div
function(doc) {
  if (doc.type == "message" && doc.message) {
    var active = doc.deletedTS == undefined;
    emit([active, doc.addedTS], doc);
  }
};
