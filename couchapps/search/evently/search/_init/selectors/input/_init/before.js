function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    select: function(event, ui) {
      $("#search-input").val(ui.item.value);
      $("#search-form").submit();
    },
    source: function(request, response) {
      if (!isNaN(parseInt(request.term))) {
        response([]);
        return;
      }
      db_search_str(app, request.term, function(data) {
        response(data.rows.map(function(row) {
          return highlight_search(row);
        }));
      });
    }
  });
};
