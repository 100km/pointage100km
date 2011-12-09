function(cb) {
    var app = $$(this).app;

    app.db.openDoc("_local/site_info", {
            success: function(data) {
                console.log(data);
                cb.call(this, data);
            },
            error: function(status) {
                console.log(status);
            }
        });
}
