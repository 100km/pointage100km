function(doc, req) {
  doc = doc || {_id: (req.id || req.uuid), type: "checkpoint"};
  var ts = Number(req.form.ts);
  if (!doc.times) {
    doc.times = [ts];
  } else {
    //TODO maybe need to optimized sorted array insertion?
    doc.times.push(ts);
    doc.times.sort(function(a,b) {return a-b});
  }
  return [doc, {
    headers : {
      "Content-Type" : "application/json"
    },
    //Tell the client we need more initialization
    body: JSON.stringify({
      need_more: !doc.race_id || doc.bib == undefined || doc.site_id  == undefined,
      lap: doc.times.length
    })
  }];
};
