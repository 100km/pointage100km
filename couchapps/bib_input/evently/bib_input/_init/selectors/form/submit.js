function() {
  var form = this;
  var bib = form.bib.value; // we are sure it's an integer because of the regexp check.
  if (! isBib(bib)) {
    return false;
  }

  // Reset the form.
  form.reset();

  // Send the data to the server and tell the items widget what we want to be the next selected item.
  bib = parseInt(bib, 10);
  submit_bib(bib, $$(this).app, null, function(lap) {
    $(form).trigger('set_selected_item', { bib: bib, lap: lap });
  });

  return false;
};
