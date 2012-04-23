function(cb) {
  var app = $$(this).app;

  fork([
    function(cb) { db_get_all_contestants(app, cb) },
    function(cb) { db_app_data(app, cb) }
  ], function(result) {
    cb(result[0][0]);
  });

}

