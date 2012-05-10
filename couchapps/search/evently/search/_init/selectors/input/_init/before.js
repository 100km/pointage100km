function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    source: function(request, response) {
      var split = request.term.trim().split(" ");
      var term = split[0];
      var opts = {
        my_limit: 10,
        startkey: term,
        endkey: increment_string_key(term)
      };
      if (split.length>1) {
        opts.term = split[1];
      }
      app.db.list("search/intersect-search", "contestants-search", opts, {
        success: function(data) {
        var regexp = new RegExp(remove_accents(term),"i");
        var regexp2;
        if (split.length>1) {
          regexp2 = new RegExp(remove_accents(split[1]),"i");
        }
          response(data.rows.map(function(row) {
            var firstvalue = row.value[row.value.match];
            var secondvalue= row.value[search_nonmatch_field(row.value.match)];
            var value = firstvalue + " " + secondvalue;
            var label = "<strong>" + firstvalue.substr(0, split[0].length) +
               "</strong>" + firstvalue.substr(split[0].length);
            if (split.length>1) {
              label = label + " " + "<strong>" + secondvalue.substr(0, split[1].length) + "</strong>" +
                 secondvalue.substr(split[1].length);
            } else {
              label = label + " " + secondvalue;
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
