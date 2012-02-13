function(cb) {
  var app = $$(this).app;

  app.db.view("bib_input/bib-problems", {
    success: cb
  });
};

