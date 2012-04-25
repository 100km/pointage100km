function(cb, x, data) {
  var app = $$(this).app;

  // Get the race_id from the div
  var race_id = parseInt(this[0].getAttribute("data-race_id"));
  var race_id_evt = data.race_id;

  // $.log("race_id of this div is " + race_id + " and race_id of the event is " + race_id_evt);

  // Do something only if we were triggered with the right race_id
  if (race_id == race_id_evt)
    db_global_ranking(app, cb, race_id);
  else
    return;
};
