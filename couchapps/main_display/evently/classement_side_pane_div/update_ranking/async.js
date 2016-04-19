function(cb) {
  var app = $$(this).app;
  var race_id = parseInt(this[0].getAttribute("data-race_id"));

  var now = new Date().getTime();
  // When race 2 has not started yet, display race 3 instead
  if (race_id == 2 && app.start_times[2] > now)
    race_id = 3;

  db_global_ranking(app, function(data) {
      // Here, if race_id is 2, then the race 2 has started.
      // If race_id is 2 and there is no timestamps for race_id 2, go to race_id 3
      if (race_id == 2 && data.data.rows.length == 0) {
        db_global_ranking(app, cb, 3);
      } else {
        cb(data);
      }
  }, race_id);
};
