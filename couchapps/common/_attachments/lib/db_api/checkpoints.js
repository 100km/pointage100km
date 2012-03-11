function add_checkpoint(bib, app, ts, cb, site_id) {
  site_id = site_id || app.site_id;
  retries(3, function(fail) {
    add_checkpoint_once(bib, app, cb, fail, ts, site_id);
  }, "add_checkpoint");
}
function add_checkpoint_once(bib, app, cb, fail, ts, site_id) {
  ts = ts || new Date().getTime();
  $.ajax({
      type: 'POST',
      url: app.db.uri + "_design/bib_input/_update/add-checkpoint/" + checkpoints_id(bib, site_id),
      data: "ts=" + ts,
      success: function(data) {
        if (data.need_more) {
          retries(3, function(fail) {
            db_checkpoints(bib, app, function(checkpoints) {
              initialize_checkpoints_once(checkpoints, bib, app, site_id, cb, fail);
            }, site_id);
          });
        } else {
          cb && cb(data.lap);
        }
      },
      error: fail
  });
}

function initialize_checkpoints_once(checkpoints, bib, app, site_id, success, fail) {
  db_race_id(bib, app, function(race_id) {
    need_write = (checkpoints.bib != bib) ||
                 (checkpoints.site_id != site_id) ||
                 (checkpoints.race_id != race_id);
    checkpoints.bib = bib;
    checkpoints.site_id = site_id;
    checkpoints.race_id = race_id;
    if (need_write) {
      app.db.saveDoc(checkpoints, { success: function() { success(checkpoints.times.length); }, error: fail });
    } else {
      success && success(checkpoints.times.length);
    }
  });
}

function db_checkpoints(bib, app, f, site_id) {
  app.db.openDoc(checkpoints_id(bib, site_id), {
    success: function(checkpoints) {
      $.log(" got " + JSON.stringify(checkpoints));
      f(checkpoints);
    },
    error: handle_not_found_or_die(function() { f({}) })
  });
}


function remove_checkpoint(bib, app, ts, cb, site_id) {
  site_id = site_id || app.site_id;
  retries(3, function(fail) {
    remove_checkpoint_once(bib, app, ts, fail, cb, site_id);
  }, "remove checkpoint");
}
function remove_checkpoint_once(bib, app, ts, fail, cb, site_id) {
  $.ajax({
      type: 'POST',
      url: app.db.uri + "_design/bib_input/_update/remove-checkpoint/" + checkpoints_id(bib, site_id),
      data: "ts=" + ts,
      success: cb,
      error: fail,
  });
}

function db_race_id(bib, app, f) {
  var invalid_race_id = 0;
  app.db.openDoc(infos_id(bib), {
    success: function(bib_info) {
      //0 is invalid as a race_id
      //valid race_ids are 1,2,4,8
      var race_id = bib_info["course"] || invalid_race_id;
      f(race_id);
    },
    error: handle_not_found_or_die(function() { f(invalid_race_id) })
  });
}
