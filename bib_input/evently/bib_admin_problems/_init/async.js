function(cb) {
  var app = $$(this).app;

  fork([
    function(cb) { get_doc(app, cb, "ping-site0") },
    function(cb) { get_doc(app, cb, "ping-site1") },
    function(cb) { get_doc(app, cb, "ping-site2") },
    function(cb) {
      app.db.view("bib_input/bib-problems", {
        success: cb
      });
    }
  ], cb);
};

