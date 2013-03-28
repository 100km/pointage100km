function(cb) {
  var app = $$(this).app;
  var _this = this;

  function set_contestant(data) {
    var race_id = parseInt(_this[0].getAttribute("data-race_id"));

    // Set field in app so that everyone can access the contestants infos
    app.contestants = [];
    _.each(data, function (item, index) {
      // $.log("In foreach : bib = " + item.bib + " item = " + JSON.stringify(item));
      app.contestants[item.bib] = item;
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


  fork([
    function(cb) { db_get_all_contestants(app, cb) },
    function(cb) { db_app_data(app, cb) }
  ], function(result) {
    set_contestant(result[0][0]);
  });

}

