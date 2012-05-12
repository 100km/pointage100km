function db_search_str_once(app, str, n, prev_data, cb) {
  var tmp = str.trim().toLowerCase().split(" ");
  var split = [tmp.slice(0, n+1).join(" ")];
  if (tmp.length > n+1) {
    split.push(tmp.slice(n+1, tmp.length).join(" "));
  }
  db_search(app, split, function(data) {
    _.each(data.rows, function(el) { el.split = split });
    prev_data.rows = prev_data.rows.concat(data.rows);
    if (n+1 == tmp.length || prev_data.length >= 10) {
      cb(prev_data);
    } else {
      db_search_str_once(app, str, n+1, prev_data, cb);
    }
  }, 10-prev_data.rows.length);
}
function db_search_str(app, str, cb) {
  db_search_str_once(app, str, 0, {rows:[]}, cb)
}
function db_search(app, split, cb, limit) {
  var opts = {
    my_limit: limit || 10,
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
