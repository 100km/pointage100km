function db_global_ranking(app, cb, race_id) {
  var last_lap = app.races_laps[race_id];

  app.db.list("main_display/global-ranking","global-ranking", {
    startkey:[race_id, -last_lap, null, null],
    endkey:[race_id+1, null, null, null]
  }, {
    success: function(data) {
      cb({data:data, race_id:race_id});
    }
  });
}

function map_contestants(data) {
  var result = {};
  var birth_date = new Date(data.naissance);

  //no error checking: we suppose all contestants have the following info in the database
  result.bib        = data.bib;
  result.name       = data.name;
  result.first_name = data.first_name;
  result.race       = data.race;
  result.cat        = cat_from_year(birth_date.getYear() , data.sexe == 2);
  result.sex        = data.sex;
  result.zipcode    = data.zipcode;
  result.city       = data.city;

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

