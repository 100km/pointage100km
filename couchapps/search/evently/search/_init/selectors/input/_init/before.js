function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    source: function(request, response) {
      if (!isNaN(parseInt(request.term))) {
        response([]);
        return;
      }
      var split = request.term.trim().toLowerCase().split(" ");
      db_search(app, split, function(data) {
        response(data.rows.map(function(row) {
          return highlight_search(split, row);
        }));
      });
    }
  });
};
