function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    source: function(request, response) {
      var term = request.term;
      app.db.view("search/contestants-search", {
        limit: 10,
        startkey: term,
        endkey: increment_string_key(term),
        success: function(data) {
          response(data.rows.map(function(row) {
            return {
              label: "<strong>"+row.key.substring(0,term.length)+"</strong>"+row.key.substring(term.length),
              value:row.key
            };
          }));
        }
      })
    }
  });
};
