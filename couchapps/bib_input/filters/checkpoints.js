function(doc, req) {
  var site_id = req.query.site_id;
  return doc.times && (!site_id || site_id == doc.site_id);
}
