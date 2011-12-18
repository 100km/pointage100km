function(data) {
  var app = $$(this).app;

  data.current_bib = app.current_bib;
  data.bibs = data.bibs || [];
  data.error = data.error || "no";
  return data;
};
