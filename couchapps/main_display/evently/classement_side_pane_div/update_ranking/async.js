function(cb) {
  var app = $$(this).app;
  var race_id = parseInt(this[0].getAttribute("data-race_id"));

  // If race_id is 2 and there is no timestamps for race_id 2, go to race_id 4
  if (race_id == 2) {
    app.db.list("main_display/global-ranking","global-ranking", {
      startkey:[race_id, null, null, null],
      endkey:[race_id+1, null, null, null]
    }, {
      success: function(data) {
        if (data.rows.length > 0)
          db_global_ranking(app, cb, race_id);
        else
          db_global_ranking(app, cb, 3);
        }
    });
  }
  else
    db_global_ranking(app, cb, race_id);
};
