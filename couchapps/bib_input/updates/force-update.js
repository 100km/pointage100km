function(doc, req) {
  var newdoc = JSON.parse(req.form.json);
  newdoc._id = req.id || req.uuid ;
  if (doc && doc._rev)
    newdoc._rev = doc._rev;
  return [newdoc, {
    headers : {
     "Content-Type" : "application/json"
     },
    body: JSON.stringify({status: "ok"})
  }];
};
