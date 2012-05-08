function(cb, x, data) {
  var app = $$(this).app;
  var bib = data.dossard;
  app.db.view("search/all-times-per-bib", {
    key: bib,
    success: function(times) {
      cb({
        times: times.rows.map(function(row) { return row.value}),
        infos: data
      });
    }
  });
}
