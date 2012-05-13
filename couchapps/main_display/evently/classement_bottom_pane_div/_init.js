function() {
  var app=$$(this).app;
  var _this=$(this);

  if (!appinfo_initialized(app))
    db_app_data(app, function() {
      $(_this).trigger("_changes");
    });
}
