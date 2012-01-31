function(doc) {
  if (doc.message) {
    var active = doc.deletedTS == undefined;
    emit([doc.target, active, doc.addedTS], doc);
  }
};
