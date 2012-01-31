function(data) {
    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;
    app.site_id = data.site_id
    app.sites = data.infos["sites"]
    app.sites_nb = app.sites.length
    app.races_names = data.infos["races_names"]
    app.kms_site = [data.infos["kms_site0"], data.infos["kms_site1"], data.infos["kms_site2"]]
    app.start_times = data.infos["start_times"]
    $(this).trigger("post_changes");

    // Set title for the document
    document.title = "Pointage " + app.sites[app.site_id];

    return {
        site_name : app.sites[app.site_id],
        site_id : data.site_id
    }
};
