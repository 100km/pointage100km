function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    source: function(request, response) {
      var split = request.term.trim().split(" ");
      var term = split[0];
      app.db.view("search/contestants-search", {
        limit: 10,
        startkey: term,
        endkey: increment_string_key(term),
        success: function(data) {
        var regexp = new RegExp("("+term+")","i");
        var regexp2 = new RegExp("( "+split[1]+")","i");
          response(data.rows.map(function(row) {
            var value = row.value[row.value.match] + " " +
                        row.value[search_nonmatch_field(row.value.match)];
            var label = value.replace(regexp, "<strong>$1</strong>");
            if (split.length>1) {
              var label2 = label.replace(regexp2, "<strong>$1</strong>");
              if (label2 != label) {
                label = label2;
              } else {
                return {};
              }
            }
            return {
              label: label,
              value: value
            };
          }).filter(function(data) { return data.value != undefined; }));
        }
      });
    }
  });
};
