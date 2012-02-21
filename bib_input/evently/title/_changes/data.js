function(data) {
    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;

    $(this).trigger("post_changes");

    // Set title for the document
    document.title = "Pointage " + app.sites[app.site_id];

    return {
        site_name : app.sites[app.site_id],
        site_id : data.site_id
    }
};
