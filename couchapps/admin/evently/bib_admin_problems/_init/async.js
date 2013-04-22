function(cb) {
  var app = $$(this).app;

  fork([
    function(cb) {
      app.db.view('admin/bib-problems', {
        success: cb
      });
    },
    function(cb) { get_ping(app, 0, cb) },
    function(cb) { get_ping(app, 1, cb) },
    function(cb) { get_ping(app, 2, cb) },
    function(cb) { get_ping(app, 3, cb) },
    function(cb) { get_ping(app, 4, cb) },
    function(cb) { get_ping(app, 5, cb) },
    function(cb) { get_ping(app, 6, cb) }
  ],
  cb);
};
