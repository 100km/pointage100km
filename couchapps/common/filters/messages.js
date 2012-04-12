function(doc, req) {
  var site_id = req.query.site_id;
  return doc.message && (doc.deletedTS == undefined)
      && (doc.target != undefined ||
          site_id != undefined ||
          site_id == doc.target);
}
