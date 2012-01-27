function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;
  var lap = app.current_lap;

  var handle_open = function(infos) {
      var race_id = infos["course"] || 0;
      var n = race_id == 0 ? 0 : 3;
      var warning = race_id == 0;
      $.log("getting " + site_id +","+ race_id +","+ lap +","+ bib);
      app.db.list("bib_input/next-contestants", "local-ranking", {
        startkey : [-site_id,race_id,-lap,null],
        endkey : [-site_id,race_id,-lap+1,null],
        bib: bib,
        n: n,
        lap : lap,
        success: function(data) {
          safe_infos = infos || empty_info();
          cb({infos:safe_infos, course:app.races_names[race_id], current_bib_time:data.bibs.pop(), bibs:data.bibs, warning: warning});
        }
      });
    }
  app.db.openDoc(infos_id(bib), {
    success: handle_open,
    error: function(stat, err, reason) {
      if(not_found(stat, err, reason)) {
        handle_open({});
      }
      else {
        $.log("stat" + JSON.stringify(stat));
        $.log("err" + JSON.stringify(err));
        $.log("reason" + JSON.stringify(reason));
        alert("Error, but not missing or deleted doc");
      }
    }
  });
};

