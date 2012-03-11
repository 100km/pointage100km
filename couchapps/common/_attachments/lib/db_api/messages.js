function unwrap_messages(data) {
  return data.rows.map(function(row) {
    return row.value;
  });
}
function _call_with_messages(app, startkey, endkey, cb) {
  app.db.view("bib_input/messages-sorted-per-site", {
    startkey: startkey,
    endkey:   endkey,
    success: function(data) {
    cb(unwrap_messages(data));
    }
  });
}
function call_with_site_messages(app, cb) {
  var id = app.site_id;
  _call_with_messages(app, [id,true], [id+1], cb);
}
function call_with_bcast_messages(app, cb) {
  _call_with_messages(app, [null,true], [false], cb);
}
function call_with_local_status(app, cb) {
  app.db.openDoc("_local/status", {
    success: cb,
    error: function(a,b,c) { cb(""); }
  });
}
function call_with_messages(app, cb) {
  fork([
    function(cb1) {call_with_site_messages(app, cb1)},
    function(cb2) {call_with_bcast_messages(app, cb2)},
    function(cb3) {call_with_local_status(app, cb3)}
  ], function(data) {
    var res = {};
    res.site_messages = data[0][0];
    res.bcast_messages = data[1][0];
    res.local_status = data[2][0];
    cb(res);
  });
}

