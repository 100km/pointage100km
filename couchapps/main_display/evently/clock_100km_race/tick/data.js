function(data) {
  var race_id = parseInt(this[0].getAttribute("data-race_id"));
  var app = $$(this).app;

  if (!appinfo_initialized(app))
    return {};

  var open_date_offset = app.start_times[race_id];
  var race_name = app.races_names[race_id];
  var res = { race_name: race_name };
  var final_time = new Date()-open_date_offset;
  if (final_time >= 0)
    res.time = int_to_datestring(final_time);
  return res;
}
