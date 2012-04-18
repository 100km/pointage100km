function(cb, wtf, request) {
  var app = $$(this).app;

  app.db.view("search/contestants-search", {
    startkey: request,
    endkey: increment_string_key(request),
    success: function(data) {
      cb(data);
    }});
};
