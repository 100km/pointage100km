function(datas) {
  // Set field in app so that everyone can access the site_id
  var app = $$(this).app;
  var pings = [];
  var infos = datas[0][0];

  copy_app_data(app, infos);

  $(this).trigger("post_changes");

  // Set title for the document
  document.title = "Administration Pointage";

  var five_minutes_ago = new Date().getTime() - 5*1000*60;
  var two_minutes_ago = new Date().getTime() - 2*1000*60;
  // Set ping infos
  for (i=0; i<app.sites_nb; i++) {
    var ping = datas[i+1][0];
    var color = ping < five_minutes_ago ? "panel-danger" :
                ping <  two_minutes_ago ? "panel-warning":
                                          "panel-success";
    pings[i] = {
      ping : format_date(new Date(ping)),
      name : app.sites[i],
      color: color
    };
  }

  return {"pings" : pings};
};
