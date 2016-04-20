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
  for (var i = doc.times.length-1; i>=0; i--) {
    if (ts == doc.times[i]) {
      //TODO maybe need to optimized sorted array insertion?
      doc.deleted_times.push(doc.times.splice(i, 1)[0]);
      doc.deleted_times.sort(function(a,b) {return a-b});
      deleted = true;
      break;
    }
  }

  return [doc, {
    headers : {
     "Content-Type" : "application/json"
     },
    body: JSON.stringify(deleted)
  }];
};
