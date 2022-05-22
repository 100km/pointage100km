function() {
  var app=$$(this).app;
  var _this=$(this);

  if (!appinfo_initialized_no_site(app)) {
    db_app_data_no_site(app, function() {
      $(_this).trigger("_changes");
    });
  } else {
    //Trigger anyway since we don't know if the changes
    //was done with the app initialized or not.
    //We could rewrite the structure in a saner way..
    $(_this).trigger("_changes");
  }
}
