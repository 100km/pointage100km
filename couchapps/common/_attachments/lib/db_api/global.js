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

