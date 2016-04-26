function db_global_ranking(app, cb, race_id) {
  var last_lap = app.races_laps[race_id];

  app.db.list("main_display/global-ranking","global-ranking-ng", {
    startkey:[race_id, -last_lap, null, null],
    endkey:[race_id+1, null, null, null]
  }, {
    success: function(data) {
      cb({data:data, race_id:race_id});
    }
  });
}
function db_teams_global_ranking(app, cb, race_id) {
  var last_lap = app.races_laps[race_id];

  app.db.list("main_display/global-ranking","global-ranking", {
    startkey:[race_id, -last_lap, null, null],
    endkey:[race_id+1, null, null, null]
  }, {
    success: function(data) {
        //Ideally, this would be done by the db.
        //We would need to insert the team_id
        //in the checkpoint document.
        //Instead, do postprocessing on the client
        //TODO do this eventually
        post_process_teams(app, data, race_id, cb);
    }
  });
}

function map_contestants(data) {
  var result = {};
  var birth_date = new Date(data.birth);

  //no error checking: we suppose all contestants have the following info in the database
  result.bib          = data.bib;
  result.name         = data.name;
  result.first_name   = data.first_name;
  result.race         = data.race;
  result.cat          = cat_from_year(birth_date.getYear());
  result.sex          = data.sex;
  result.zipcode      = data.zipcode;
  result.city         = data.city;
  result.handisport   = data.handisport;
  result.championship = data.championship;

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

function _post_process_teams(checkpoints_data, teams_data) {
  var last_time_of_team = {};
  var team_idx = {};
  var ids = teams_data.ids;
  var names = teams_data.names;
  if (checkpoints_data.rows.length == 0) return [];
  var tmp = checkpoints_data.rows[0].contestants;
  _.each(tmp, function(checkpoint) {
    var bib = checkpoint.value.bib;
    var team_id = ids[bib]["team_id"];
    last_time_of_team[team_id] = last_time_of_team[team_id] || [];
    var time = checkpoint.key[3];
    last_time_of_team[team_id].push({time: time, checkpoint: checkpoint});
    last_time_of_team[team_id].sort(function (a,b) { return b.time - a.time});
  });
  tmp = _.filter(tmp, function(checkpoint) {
    var bib = checkpoint.value.bib;
    var team_id = ids[bib]["team_id"];
    return bib == last_time_of_team[team_id][0]["checkpoint"]["value"]["bib"];
  });
  var res = _.map(tmp, function(checkpoint) {
    var bib = checkpoint.value.bib;
    var team_id = ids[bib]["team_id"];
    return {team_id: team_id, team_name: names[team_id], members: last_time_of_team[team_id]};
  });
  res.sort(function(a,b) { return b.members.length - a.members.length; }); //needs a stable sort
  return res;
}
function post_process_teams(app, checkpoints_data, race_id, cb) {
  db_teams(app, function(teams_data) {
    var data = _post_process_teams(checkpoints_data, teams_data);
    cb({checkpoints_data:data, teams_data:teams_data, race_id:race_id});
  });
}
