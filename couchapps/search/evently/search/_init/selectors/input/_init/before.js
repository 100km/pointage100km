function() {
  var app = $$(this).app;
  $(this).autocomplete({
    source: function(request, response) {
      var term = request.term;
      app.db.view("search/contestants-search", {
        limit: 10,
        startkey: request.term,
        endkey: increment_string_key(request.term),
        success: function(data) {
          response(data.rows.map(function(row) {
            return row.key;
          }));
        }
      })
    }
  });
};
