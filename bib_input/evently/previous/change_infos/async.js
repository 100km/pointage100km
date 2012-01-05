function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;

  app.db.view("bib_input/bib_info", {
    limit: 1,
    startkey : bib,
    endkey : bib + 1,
    success: function(infos) {
      app.db.view("bib_input/contestant-checkpoints", {
        limit: 1,
        startkey : [site_id, bib],
        endkey : [site_id, bib + 1],
        success: function(doc_times_for_bib) {
          var lap = doc_times_for_bib.rows[0].value.times.length;
          app.db.list("bib_input/next-contestants", "local-ranking", {
            startkey : [-site_id,-lap,null],
            endkey : [-site_id+1,-lap+1,null],
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
    }
  });
};

