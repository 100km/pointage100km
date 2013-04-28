function(data) {
  console.log(data);
  var p = {};
  var checkpoints_data = data.checkpoints_data;
  var teams_data = data.teams_data;
  p.items = [];
  var items = [];
  var app = $$(this).app;
  var race_id = data.race_id;
  var start_time = app.start_times[race_id];
  p.race_id = race_id;
  p.race_name = app.races_names[race_id];

  if (! checkpoints_data)
    return p;

  _.each(checkpoints_data, function(team, idx) {
    var item = {}
    var lap = team.members.length;
    var last_bib_checkpoint = team.members[0]["checkpoint"];
    var team_id = team.team_id;
    item.rank = idx+1;
    item.team_name = team.team_name;
    item.kms     = site_lap_to_kms(app, last_bib_checkpoint.value.site_id, lap)
    item.time    = int_to_datestring(last_bib_checkpoint.key[3] - start_time);
    item.is_odd=(idx%2)==1;
    var members = _.filter(teams_data.ids, function(member) {
      return member.team_id == team_id;
    });
    _.each(members, function(a, idx) {
      members[idx]["is_odd"] = item.is_odd;
    });
    item.members=members;
    item.rowspan=members.length+1;
    items.push(item);
  });
  p.items=items;
  console.log("p", p);
  
  return p;
}
