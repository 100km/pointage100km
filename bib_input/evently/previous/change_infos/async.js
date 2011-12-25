function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;

  app.db.list("bib_input/next-contestants", "local-ranking", {
    startkey : [-site_id,null,null],
    endkey : [-site_id+1,null,null],
    bib: bib,
    n: 3,
    success: function(data) {
      cb(data);
    }
  });
};

