function(data) {
    // Set title for the document
    document.title = "Pointage " + data.name;

    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;
    app.site_id = data.site_id
    app.sites = data.sites
    app.sites_nb = data.sites.length
    $(this).trigger("post_changes");

    return {
        site_name : data.name,
        site_id : data.site_id
    }
};
