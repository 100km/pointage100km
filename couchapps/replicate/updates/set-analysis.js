// Insert a new analysis
function(doc, req) {
  var analysis = JSON.parse(req.body);

  // Do some sanity checks
  var expectedId = "analysis-" + analysis.bib;
  if (expectedId !== req.id)
    return [null, { code: 404, body: "bad id " + req.id + " for contestant " + analysis.bib }];
  if (expectedId != analysis._id)
    return [null, { code: 404, body: "found id " + analysis._id + " instead of " + expectedId }];
  if (doc)
    analysis._rev = doc._rev;
  return [analysis, {
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(analysis)
  }];
}
