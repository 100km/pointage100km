function(data) {
    // At this point, data (like site_id) has been copied in app
    var app = $$(this).app;

    $(this).trigger("app_info_changed");

    // Set title for the document
    document.title = "Pointage " + app.sites[app.site_id];

    return {
        site_name : app.sites[app.site_id],
        site_id : app.site_id
    }
};
