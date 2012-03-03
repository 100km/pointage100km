function(infos) {
    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;
    app.sites = infos["sites"]
    app.sites_nb = app.sites.length
    app.races_names = infos["races_names"]
    app.kms_offset = [infos["kms_offset_site0"], infos["kms_offset_site1"], infos["kms_offset_site2"]]
    app.kms_lap = infos["kms_lap"]
    app.start_times = infos["start_times"]
    $(this).trigger("post_changes");

    // Set title for the document
    document.title = "Administration Pointage";

  return;
};
