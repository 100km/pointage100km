function db_previous(app, site_id, data, cb) {
  var bib = data.bib;
  var lap = data.lap;
  var ts = data.ts;
  var infos = data;
  var race_id = infos["course"] || 0;
  var n = race_id == 0 ? 0 : 3;
  var warning = race_id == 0;
  var kms = site_lap_to_kms(app, site_id, lap);

  if (warning) {
    cb({warning:warning});
  }

  function get_rank(cb) {
    app.db.view("bib_input/local-rank", {
      startkey : [-site_id,race_id,-lap,0],
      endkey : [-site_id,race_id,-lap,ts],
      success : cb
    });
  }

  function get_predecessors(cb) {
    app.db.view("bib_input/local-predecessors", {
      startkey : [-site_id,race_id,-lap,ts],
      endkey : [-site_id,race_id,-lap-1,{}],
      descending : true,
      limit : n,
      success : cb
    });
  }

  function get_average(cb) {
    app.db.view("bib_input/times-per-bib", {
      startkey : [bib, ts],
      limit : 5,
      descending : true,
      success: function(local_avg_data) {
        var avg_present = false;
        var i = 0;
        while ((! avg_present) && (i<5)) {
          i++;
          // Check that it's the same bib and that it is a lower combination [lap,site_id]
          var avg_present = local_avg_data.rows[i] &&
            (local_avg_data.rows[i].key[0] == bib) &&
            ([local_avg_data.rows[i].value[1],local_avg_data.rows[i].value[0]] < [lap, site_id]);
        }
        var result = {avg_present:avg_present};
        if (avg_present) {
          result.last_site = local_avg_data.rows[i].value[0];
          result.last_timestamp = local_avg_data.rows[i].key[1];
          result.last_lap = local_avg_data.rows[i].value[1];
        }
        cb(result);
      }
    });
  }


  fork([
    get_rank,
    get_predecessors,
    get_average,
  ], function(data) {
    var res = {};
    res.rank = data[0][0].rows[0].value;
    res.predecessors = data[1][0].rows;
    res.average = data[2][0];
    res.infos = infos || empty_info();
    res.course = app.races_names[race_id];
    res.bib_time = ts;
    res.warning = warning;
    res.kms = kms;
    res.limit = n;
    res.bib = bib;
    res.lap = lap;
    res.ts = ts;

    cb(res);
  });
}
