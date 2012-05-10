function() {
  var app = $$(this).app;
  $(this).autocomplete({
    html: true,
    source: function(request, response) {
      var split = request.term.trim().toLowerCase().split(" ");
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
            var value;
            var label1 = "<strong>" + firstvalue.substr(0, split[0].length) +
               "</strong>" + firstvalue.substr(split[0].length);
            var label2;
            if (split.length>1) {
              label2 = "<strong>" + secondvalue.substr(0, split[1].length) + "</strong>" +
                secondvalue.substr(split[1].length);
            } else {
              label2 = secondvalue;
            }
            var label;
            function capitaliseFirstLetter(string) {
            return string.charAt(0).toUpperCase() + string.slice(1);
            }
            if (row.value.match == "nom") {
              label = "<span class=family-name>" + label1 + "</span> <span class=firstname>" + label2 + "</span"
              value = firstvalue.toUpperCase() + " " + capitaliseFirstLetter(secondvalue);
            } else {
              label = "<span class=firstname>" + label1 + "</span> <span class=family-name>" + label2 + "</span"
              value = capitaliseFirstLetter(firstvalue) + " " + secondvalue.toUpperCase();
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
