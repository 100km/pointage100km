function(cb) {
  var app = $$(this).app;

  //TODO hardoced number of sites
  fork([
    function(cb) { get_ping(app, 0, cb) },
    function(cb) { get_ping(app, 1, cb) },
    function(cb) { get_ping(app, 2, cb) },
    function(cb) { get_ping(app, 3, cb) },
    function(cb) { get_ping(app, 4, cb) },
    function(cb) { get_ping(app, 5, cb) },
    function(cb) { get_ping(app, 6, cb) }
  ], function(pings) {
      app.db.list('admin/bib-problems', "bib-problems", {
        pings: JSON.stringify(_.flatten(pings))
      }, {
        success: cb
      });
  });
};
