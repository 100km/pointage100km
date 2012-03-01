function(doc, req) {
  doc = doc || {_id: "_local/status"};
  doc.message = req.form.message;
  return [doc, {
     headers : {
      "Content-Type" : "application/json"
     },
     body : JSON.stringify({status: "ok"})
  }];
};
