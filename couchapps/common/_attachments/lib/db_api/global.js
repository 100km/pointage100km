function db_global_ranking(app, cb, race_id) {
  app.db.list("main_display/global-ranking","global-ranking", {
    startkey:[race_id, null, null, null],
    endkey:[race_id+1, null, null, null]
  }, {
    success: function(data) {
      cb(data);
    }
  });
}

function map_contestants(data) {
  var result = {};

  //no error checking: we suppose all contestants have the following info in the database
  result.dossard = data.dossard;
  result.nom     = data.nom;
  result.prenom  = data.prenom;
  result.course  = data.course;

  return result;
}

function db_get_all_contestants(app, cb) {
  app.db.view("common/all_contestants", {
    success: function(data) {
      var unwrapped_data = unwrap_data(data);
      cb(unwrapped_data.map(map_contestants));
    }
  });
}

