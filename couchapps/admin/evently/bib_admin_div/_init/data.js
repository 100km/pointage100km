function(infos) {
    // Set field in app so that everyone can access the site_id
    var app = $$(this).app;

    copy_app_data(app, infos);

    $(this).trigger("post_changes");

    // Set title for the document
    document.title = "Administration Pointage";

  return;
};
