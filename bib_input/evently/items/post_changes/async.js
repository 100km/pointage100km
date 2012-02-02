function(cb) {
  var app = $$(this).app;
  var site_id = app.site_id;

  // startkey and endkey are invrsed because descending is true
  app.db.view("bib_input/recent-checkpoints", {
    descending: true,
    limit : 50,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      var allDocs_keys=_.uniq(data.rows.map(function(row) {
        return infos_id(row.value.bib);
      }));
      app.db.allDocs({
        keys: allDocs_keys,
        include_docs: true,
        success: function(infos) {
          var hash = {};
          _.each(infos.rows, function(row) {
            hash[row.doc.dossard] = {
              prenom: row.doc.prenom,
              nom: row.doc.nom,
              course: row.doc.course
              };
          });
          _.each(data.rows, function(row) {
            row.infos = hash[row.value.bib];
          });
          cb(data.rows);
        }
      });
    }
  });
};

