// Return changes in stalkers
function (doc, req) {
  return doc.type == "contestant" && doc.stalkers;
}
