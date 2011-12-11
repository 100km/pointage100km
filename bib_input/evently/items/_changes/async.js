function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  if (site_id == undefined) {
    // TODO better handling of events
    site_id = 0;
  }
  // startkey and endkey are invrsed because descending is true
  app.db.view("bib_input/recent-checkpoints", {
    descending: true,
    limit : 50,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      cb(data);
    }
  });
};

