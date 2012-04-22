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
            var value = row.value.nom + " " + row.value.prenom;
            var match = row.value[row.value.match];
            var label = "<strong>"+match.substring(0,term.length)+"</strong>"+match.substring(term.length);
            if (row.value.match == "nom") {
              label = label + " " + row.value.prenom;
            } else {
              label = row.value.nom + " " + label;
            }
            return {
              label: label,
              value: value
            };
          }));
        }
      });
    }
  });
};
