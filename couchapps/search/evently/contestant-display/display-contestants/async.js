function(cb, wtf, request) {
  var app = $$(this).app;

  var split = request.trim().toLowerCase().split(" ");
  var key = split[0];
  var opts = {
    my_limit: 10,
    startkey: key,
    endkey: increment_string_key(key)
  };
  if (split.length>1) {
    opts.term = split[1];
  }
  app.db.list("search/intersect-search", "contestants-search", opts, {
    success: function(data) {
      var res = data.rows;
      res.request = request;
      cb(res);
    }});
};
