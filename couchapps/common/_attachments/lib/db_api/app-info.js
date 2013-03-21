function copy_app_data(app, infos) {
  app.sites = infos["sites"]
  app.sites_nb = app.sites.length
  app.races_names = infos["races_names"]
  app.kms_offset = infos["kms_offset"]
  app.kms_lap = infos["kms_lap"]
  app.start_times = infos["start_times"]
  app.cat_names = infos["cat_names"]
}

function appinfo_initialized(app) {
  return _.all([
    "site_id", "sites",
    "sites_nb", "races_names",
    "kms_offset", "kms_lap",
    "start_times"
  ], function(key) {
    return app[key] != undefined;
  });
}

function db_app_data(app, cb) {
  fork([
    function(cb) { get_doc(app, cb, "site-info") },
    function(cb) { get_doc(app, cb, "infos") }
  ], function(result) {
    app.site_id = result[0][0]["site-id"];
    copy_app_data(app, result[1][0]);
    cb();
  });
}
