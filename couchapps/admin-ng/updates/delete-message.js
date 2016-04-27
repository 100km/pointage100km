function(doc, req) {
  if (doc && doc.deletedTS === undefined) {
    doc.deletedTS = Date.now();
    return [doc, {code: 204}];
  }
  if (doc) {
    return [null, {code: 202}];
  }
  return [null, {code: 404}];
}
