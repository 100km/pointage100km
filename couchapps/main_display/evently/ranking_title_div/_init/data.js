function(data) {
  var app = $$(this).app;

  // Set field in app so that everyone can access the contestants infos
  app.contestants = data;

  $("#left_pane_div").trigger("update_ranking", {race_id:1});
  $("#right_pane_div").trigger("update_ranking", {race_id:2});
  //$(this).trigger("_changes");

  return;
};
