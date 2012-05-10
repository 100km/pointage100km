function(cb) {
  var app = $$(this).app;

  fork([
    function(cb) { get_ping(app, 0, cb) },
    function(cb) { get_ping(app, 1, cb) },
    function(cb) { get_ping(app, 2, cb) },
    function(cb) { get_doc(app, cb, "infos") }
  ],
  cb);

}

