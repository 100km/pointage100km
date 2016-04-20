function(doc, req) {
  var ts = Number(req.form.ts);
  if (!isFinite(ts))
    return [doc, {
      code: 400,
      headers : {
        "Content-Type" : "application/json"
      },
      body: JSON.stringify("invalid timestamp")
    }];

  var deleted = false;
  doc.deleted_times = doc.deleted_times || [];
  var idx = doc.times.indexOf(ts);
  if (idx > -1) {
    doc.times.splice(idx, 1);
    doc.deleted_times.push(ts);
    doc.deleted_times.sort(function(a,b) {return a-b;});
  }

  return [doc, {
    headers : {
     "Content-Type" : "application/json"
     },
    body: JSON.stringify(idx > -1)
  }];
}
