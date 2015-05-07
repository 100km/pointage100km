function(doc, req) {
  var data = JSON.parse(req.body);
  var stalker = data.stalker;
  var index = doc.stalkers.indexOf(stalker);
  if (data.operation == "add" && index == -1) {
    doc.stalkers.push(stalker);
  } else if (data.operation == "remove" && index != -1) {
    doc.stalkers.splice(index, 1);
  }
  doc.stalkers = doc.stalkers.sort();
  return [doc, {
    headers : {
      "Content-Type" : "application/json"
    },
    body: JSON.stringify(doc.stalkers)
  }];
};
