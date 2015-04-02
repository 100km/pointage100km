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
      if (race_id==5) {
        $("#single_ranking_div").trigger("update_ranking");
      } else {
        $("#single_ranking_div").trigger("update_ranking");
      }
    }
    else {
      $("#left_pane_div").trigger("update_ranking");
      $("#right_pane_div").trigger("update_ranking");
    }

    return;
  };


  fork([
    function(cb) { db_get_all_contestants(app, cb) },
    function(cb) {
      if (!appinfo_initialized_no_site(app)) {
        db_app_data_no_site(app, cb);
      } else {
        cb();
      }
    }
  ], function(result) {
    set_contestant(result[0][0]);
  });

}

