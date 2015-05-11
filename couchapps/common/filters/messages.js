function(doc, req) {
  var site_id = req.query.site_id;
  return (doc.type == "message" || doc.type == "status")
      && (doc.message || doc.message == "")
      && (doc.target != undefined ||
          site_id != undefined ||
          site_id == doc.target);
}
