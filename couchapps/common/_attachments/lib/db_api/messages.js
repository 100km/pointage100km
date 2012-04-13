function unwrap_messages(data) {
  return data.rows.map(function(row) {
    return row.value;
  });
}

function _db_messages(app, startkey, endkey, cb) {
  app.db.view("common/messages-sorted-per-site", {
    startkey: startkey,
    endkey:   endkey,
    success:  function(data) {
      cb(unwrap_messages(data));
    }
  });
}

function db_site_messages(app, cb, site_id) {
  if (site_id != undefined)
    _db_messages(app, [site_id,true], [site_id+1], cb);
  else
    cb([]);
}

function db_bcast_messages(app, cb) {
  _db_messages(app, [null,true], [false], cb);
}

function db_local_status(app, cb) {
  app.db.openDoc("status", {
    success: cb,
    error: function(a,b,c) { cb(""); }
  });
}

function db_messages(app, cb, site_id) {
  fork([
    function(cb1) {db_site_messages(app, cb1, site_id)},
    function(cb2) {db_bcast_messages(app, cb2)},
    function(cb3) {db_local_status(app, cb3)}
  ], function(data) {
    var res = {};
    res.site_messages = data[0][0];
    res.bcast_messages = data[1][0];
    res.local_status = data[2][0];
    cb(res);
  });
}

