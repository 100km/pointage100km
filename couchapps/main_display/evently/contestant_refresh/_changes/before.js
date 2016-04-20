function(cb) {
  var app = $$(this).app;

  //Ignore multiple changes in the same 30 seconds window
  //This also avoids conflicts with the first load in ranking_title_div
  if (typeof(main_display_contestant_refreshing) == 'undefined' ||
      !main_display_contestant_refreshing) {
    main_display_contestant_refreshing = true;
    setTimeout(function() {
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
        data = result[0][0];
        app.contestants = app.contestants || [];
        _.each(data, function (item, index) {
          app.contestants[item.bib] = item;
        });
        main_display_contestant_refreshing = false;
      });
    }, 30000);
  }
}
