function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;

  app.db.list("bib_input/global-ranking","rankings", {
    limit : 50,
    success: function(data) {
      cb(data);
    }
  });
};

