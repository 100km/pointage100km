function(cb, wtf, request) {
  var app = $$(this).app;

  var request_int = parseInt(request);
  if (!isNaN(request_int)) {
    app.db.view("search/contestants-search", {
      success: function(data) {
        var res = data.rows;
        res.request = request;
          cb(res);
      },
      error: function() {
        var res = [];
        res.request = request;
        cb(res);
      },
      startkey: request_int,
      endkey: request_int,
      inclusive_end: true
    });
  } else {
    db_search_str(app, request, function(data) {
      var res = data.rows;
      res.request = request;
      cb(res);
    });
  }
};
