function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;
  var bib_int = parseInt(bib);

  app.db.view("bib_input/bib_info", {
    limit: 1,
    startkey : bib_int,
    endkey : bib_int + 1,
    success: function(infos) {
      app.db.list("bib_input/next-contestants", "local-ranking", {
        startkey : [-site_id,null,null],
        endkey : [-site_id+1,null,null],
        bib: bib,
        n: 3,
        success: function(data) {
          safe_infos = (infos.rows[0] && infos.rows[0].value) || empty_info();
          cb({infos:safe_infos, bibs:data.bibs});
        }
      });
    }
  });
};

