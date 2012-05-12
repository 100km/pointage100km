function db_search(app, split, cb) {
  var opts = {
    my_limit: 10,
    startkey: split[0],
    endkey: increment_string_key(split[0])
  };
  if (split.length>1) {
    opts.term = split[1];
  }
  app.db.list("search/intersect-search", "contestants-search", opts, {
    success: cb
  });
}
