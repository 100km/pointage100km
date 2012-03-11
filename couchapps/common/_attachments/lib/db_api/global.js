
function db_global_ranking(app, cb) {
  app.db.list("bib_input/global-ranking","global-ranking", {
    limit : 50,
    success: function(data) {
      cb(data);
    }
  });
}

