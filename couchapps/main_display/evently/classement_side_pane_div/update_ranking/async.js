function(cb) {
  var app = $$(this).app;
  var race_id = parseInt(this[0].getAttribute("data-race_id"));

  var now = new Date().getTime();

  db_global_ranking(app, cb, race_id);
};
