function(cb, wtf, request) {
  var app = $$(this).app;

  var request_int = parseInt(request);
  if (!isNaN(request_int)) {
    app.db.openDoc(infos_id(request_int), {
      success: function(data) {
        var res = [{
          match: "dossard",
          prenom: data.prenom || "",
          nom: data.nom || "",
          dossard: data.dossard
        }];
        res.request = request;
        cb([{value: data}]);
      },
      error: function() {
        cb([]);
      }
    });
  } else {
    var split = request.trim().toLowerCase().split(" ");
    db_search(app, split, function(data) {
      var res = data.rows;
      res.request = request;
      cb(res);
    });
  }
};
