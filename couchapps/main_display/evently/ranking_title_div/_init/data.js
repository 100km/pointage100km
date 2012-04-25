function(data) {
  var app = $$(this).app;

  // Set field in app so that everyone can access the contestants infos
  app.contestants = [];
  _.each(data, function (item, index) {
    // $.log("In foreach : bib = " + item.dossard + " item = " + JSON.stringify(item));
    app.contestants[item.dossard] = item;
  });

  $("#left_pane_div").trigger("update_ranking", {race_id:1});
  $("#right_pane_div").trigger("update_ranking", {race_id:2});

  return;
};
