function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;
  if (!appinfo_initialized)
    cb([]);

  // startkey and endkey are inversed because descending is true
  app.db.view("bib_input/recent-checkpoints", {
    descending: true,
    include_docs: true,
    limit : 30,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      _.each(data.rows, function(row) {
        if (row.doc) {
          row.infos = {
            prenom: row.doc.prenom,
            nom: row.doc.nom,
            course: row.doc.course
          };
        }
      });

      cb(data.rows);
    }
  });
};

