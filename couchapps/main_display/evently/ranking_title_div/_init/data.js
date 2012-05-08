function(data) {
  var app = $$(this).app;
  var race_id = parseInt(this[0].getAttribute("data-race_id"));


  // Set field in app so that everyone can access the contestants infos
  app.contestants = [];
  _.each(data, function (item, index) {
    // $.log("In foreach : bib = " + item.dossard + " item = " + JSON.stringify(item));
    app.contestants[item.dossard] = item;
  });

  if (race_id) {
    // We are in the title for only one race
    $("#single_ranking_div").trigger("update_ranking", {race_id:race_id});
  }
  else {
    $("#left_pane_div").trigger("update_ranking", {race_id:1});
    $("#right_pane_div").trigger("update_ranking", {race_id:2});
  }

  return;
};
