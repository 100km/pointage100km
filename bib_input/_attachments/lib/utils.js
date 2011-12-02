function call_with_lap(bib, app, f) {
  app.db.view("bib_input/current-lap", {
    key: bib,
    group: true,
    success: function(data) {
      var lap = (data["rows"][0] && data["rows"][0]["value"] + 1) || 1;
      f.call(null, lap);
    }
  });
}
