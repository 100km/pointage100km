function(doc, req) {
  return doc.message && (doc.deletedTS == undefined);
}
