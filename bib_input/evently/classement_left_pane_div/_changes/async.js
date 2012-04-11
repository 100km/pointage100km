function(cb) {
  var app = $$(this).app;

  app.db.list("bib_input/global-ranking","global-ranking", {
    limit : 50,
    success: function(data) {
      cb(data);
    }
  });
};

