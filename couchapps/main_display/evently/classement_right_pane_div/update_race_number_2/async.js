function(cb) {
    var app = $$(this).app;
    
    $.log("right_pane_async");

    db_global_ranking(app, cb, 2);
};

