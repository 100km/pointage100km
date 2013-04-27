function(cb) {
  var app = $$(this).app;

  if (!appinfo_initialized(app)) {
    cb({site_id: undefined, data: []});
    return;
  }

  var site_id = app.sites_nb - 1;

  // startkey and endkey are inversed because descending is true
  app.db.view("bib_input/recent-checkpoints", {
    descending: true,
    include_docs: true,
    limit : 5,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      _.each(data.rows, function(row) {
        if (row.doc) {
          row.infos = {
            first_name: row.doc.first_name,
            name: row.doc.name,
            race: row.doc.race
          };
        }
      });

      cb({site_id: site_id, data: data.rows});
    }
  });
};

