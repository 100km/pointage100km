function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;
  var lap = app.current_lap;

  app.db.view("bib_input/bib_info", {
    limit: 1,
    startkey : bib,
    endkey : bib + 1,
    success: function(infos) {
      var race_id = infos.rows[0] && infos.rows[0].value["course"];
      app.db.list("bib_input/next-contestants", "local-ranking", {
        startkey : [-site_id,race_id,-lap,null],
        endkey : [-site_id,race_id,-lap+1,null],
        bib: bib,
        n: 3,
        lap : lap,
        success: function(data) {
          safe_infos = (infos.rows[0] && infos.rows[0].value) || empty_info();
          cb({infos:safe_infos, bibs:data.bibs});
        }
      });
    }
  });
};

