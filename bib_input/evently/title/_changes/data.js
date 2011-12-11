function(data) {
    // Set title for the document
    document.title = "Pointage " + data.name;

    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;
    app.site_id = data.site_id

    return {
        site_name : data.name,
        site_id : data.site_id
    }
};
