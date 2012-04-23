function(cb, wtf, request) {
  var app = $$(this).app;

  var split = request.replace(/[^A-Za-z ]/g, "").trim().split(" ");
  var key = split[0];
  app.db.view("search/contestants-search", {
    startkey: key,
    endkey: increment_string_key(key),
    success: function(data) {
      var res;
      if (split.length > 1) {
        var regexp = new RegExp(split[1], "i");
        res = _.filter(data.rows, function(row) {
            return regexp.test(row.value[search_nonmatch_field(row.value.match)]);
        });
      } else {
        res = data.rows;
      }
      cb(res);
    }});
};
