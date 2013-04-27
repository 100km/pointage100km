function(cb, x, data) {
  var app = $$(this).app;

  // Get the race_id from the div
  var race_id = parseInt(this[0].getAttribute("data-race_id"));
  db_global_ranking(app, cb, race_id);
};
