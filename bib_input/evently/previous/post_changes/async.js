function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;

  app.db.view("bib_input/recent-checkpoints", {
    limit: 1,
    descending: true,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      if (data.rows.length > 0) {
        var bib = data.rows[0]["value"]["bib"];
        app.db.list("bib_input/next-contestants", "rankings", {
          bib: bib,
          n: 3,
          success: function(data) {
            cb(data);
          }
        });
      } else
        cb({});
    }
  });
};

