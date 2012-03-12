
function db_global_ranking(app, cb) {
  app.db.list("main_display/global-ranking","global-ranking", {
    limit : 50,
    success: function(data) {
      cb(data);
    }
  });
}

