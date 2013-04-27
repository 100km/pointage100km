//Safe=true will prevent showing checkpoints
//that are in laps gt than the max lap
function db_recent(app, cb, site_id, limit, safe) {
  site_id = site_id || app.site_id;
  safe = safe || false;

  var opts = {};
  if (!safe) {
    //Since we filter in client side in safe mode,
    //only put the limit in unsafe mode
    //Hopefully it's not to expansive on the DB
    opts.limit = limit;
  }

  // startkey and endkey are inversed because descending is true
  app.db.view("bib_input/recent-checkpoints", _.extend({
    descending: true,
    include_docs: true,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      _.each(data.rows, function(row) {
        if (row.doc) {
          row.infos = {
            first_name: row.doc.first_name,
            name: row.doc.name,
            race: row.doc.race
          };
        }
      });

      if (safe) {
        var res = [];
        var rows_length = data.rows.length;
        var rows = data.rows;
        for (var i = 0; i<rows_length; i=i+1) {
          var row = rows[i];
          if (!row.infos) {
            //We don't know the race for this checkpoint so keep it
            res.push(row)
          } else {
            var max_lap = app.races_laps[row.infos.race];
            if (row.value.lap <= max_lap) {
              res.push(row)
            }
          }
          if (res.length == limit) {
            break;
          }
        }
        data.rows=res;
      }
      cb(data.rows);
    }
  }, opts));
}
