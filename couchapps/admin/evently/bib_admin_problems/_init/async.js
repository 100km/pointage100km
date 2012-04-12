function(cb) {
  var app = $$(this).app;

  fork([
    function(cb) { get_ping(app, 0, cb) },
    function(cb) { get_ping(app, 1, cb) },
    function(cb) { get_ping(app, 2, cb) },
    function(cb) {
      app.db.view('admin/bib-problems', {
        success: cb
      });
    }
  ],
  cb);
};

function get_ping(app, ping, callback) {
  app.db.view('admin/alive', {
    key: ping,
    reduce: true,
    success: function(view) { callback(view.rows[0].value.max); },
    error: function() { callback(); }
  });
};
