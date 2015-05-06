// Return checkpoints pertaining to the stalked bibs passed
// as a comma separated list
function (doc, req) {
  var stalked = req.query.stalked.split(",").map(Number)
  return doc.type == "checkpoint" && stalked.indexOf(doc.bib) != -1;
}
