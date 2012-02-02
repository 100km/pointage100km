function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  var bib = app.current_bib;
  var lap = app.current_lap;
  var kms = app.kms_site[site_id][lap] || 0;

  var handle_open = function(infos) {
    var race_id = infos["course"] || 0;
    var n = race_id == 0 ? 0 : 3;
    var warning = race_id == 0;

    app.db.list("bib_input/next-contestants", "local-ranking", {
      startkey : [-site_id,race_id,-lap,null],
      endkey : [-site_id,race_id,-lap+1,null],
      bib: bib,
      n: n,
      lap : lap,
      start_time : app.start_times[race_id],
      kms : kms,
      success: function(data) {
        safe_infos = infos || empty_info();
        app.db.view("bib_input/times-per-bib", {
          startkey : [bib,data.bib_time],
          limit : 2,
          descending : true,
          success: function(local_avg_data) {
            var avg_present = (local_avg_data.rows[1] != undefined) && (local_avg_data.rows[1].key[0] == bib);
            cb({
              infos:safe_infos,
              course:app.races_names[race_id],
              current_bib_time:data.bibs.pop(),
              bibs:data.bibs,
              warning: warning,
              kms:kms,
              global_average:data.global_average,
              avg_present:avg_present,
              last_site:local_avg_data.rows[1].value[0],
              last_timestamp:local_avg_data.rows[1].key[1],
              last_lap:local_avg_data.rows[1].value[1],
              bib_time:data.bib_time
            });
          }
        });
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

