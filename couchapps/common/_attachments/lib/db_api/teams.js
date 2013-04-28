function db_teams(app, cb) {
  app.db.view("common/team-members", {
    success: function(data) {
      var ids = {};
      var names = {};
      _.each(data.rows, function(row) {
        ids[row.value.bib] = row.value;
        names[row.value.team_id] = row.value.team_name;
      });
      cb({ids: ids, names: names});
    }
  });
}
