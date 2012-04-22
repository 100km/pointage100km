function(cb, wtf, request) {
  var app = $$(this).app;

  var split = request.replace(/[^A-Za-z ]/g, "").trim().split(" ");
  var key = split[0];
  app.db.view("search/contestants-search", {
    startkey: key,
    endkey: increment_string_key(key),
    success: function(data) {
      var res;
      if (split.length > 1) {
        res = _.filter(data.rows, function(row) {
          return (row.value.prenom == key && row.value.nom == split[1]) ||
                 (row.value.nom == key && row.value.prenom == split[1]);
          });
      } else {
        res = data.rows;
      }
      cb(res);
    }});
};
