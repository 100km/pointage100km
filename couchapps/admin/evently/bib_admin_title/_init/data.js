function(datas) {
  // Set field in app so that everyone can access the site_id
  var app = $$(this).app;
  var pings = [];
  var infos = datas[3][0];

  copy_app_data(app, infos);

  $(this).trigger("post_changes");

  // Set title for the document
  document.title = "Administration Pointage";

  // Set ping infos
  for (i=0; i<3; i++)
    pings[i] = {ping : format_date(new Date(datas[i][0])), name : app.sites[i]};

  return {"pings" : pings};
};
