function() {
  var form = $(this)[0];
  var bib = form["bib"].value; // we are sure it's an integer because of the regexp check.
  if (! isBib(bib)) return false;
  bib = parseInt(bib, 10);
  form.reset();

  var app = $$(this).app;
  submit_bib(bib, app, null, function(lap) {
    $('#items').data('selected_item', { bib: bib, lap: lap });
  });

  return false;
};
