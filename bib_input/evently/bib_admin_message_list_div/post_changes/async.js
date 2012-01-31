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
      app.db.view("bib_input/bib_info", {
        success: function(data_info) {
          var map = {};
          for (var i=0; i<data_info.rows.length; i++) {
            map[data_info.rows[i].value.dossard] = {prenom:data_info.rows[i].value.prenom, nom:data_info.rows[i].value.nom, course:data_info.rows[i].value.course};
          }
          var new_data = data.rows.map(function(r) {
            return {value:r.value, key:r.key, infos:map[r.value.bib]};
          });
          cb(new_data);
        }
      });
    }
  });
};

