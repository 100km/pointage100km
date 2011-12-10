function(cb) {
  var app = $$(this).app;
  app.db.view("bib_input/recent-checkpoints", {
    limit: 1,
    descending: true,
    success: function(data) {
      var bib = data.rows[0]["value"]["bib"];
      app.db.list("bib_input/next-contestants", "rankings", {
        bib: bib,
        success: function(data) {
          cb(data);
        }
      });
    }
  });
};

