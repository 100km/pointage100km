function() {
  var form = this;
  var bib = form.bib.value; // we are sure it's an integer because of the regexp check.
  var app = $$(this).app;
  if (! isBib(bib) || (app.site_id == undefined)) {
    return false;
  }

  // Reset the form.
  form.reset();

  // Send the data to the server and tell the items widget what we want to be the next selected item.
  bib = parseInt(bib, 10);
  add_checkpoint(bib, app, null, function(lap) {
    $(form).trigger('set_selected_item', { bib: bib, lap: lap });

    //Only keep the last 20 times clocked on this page, we show the 10 most recents.
    //It will work if people always delete less than 10 times consecutively.
    app.bibs_clocked_here.unshift({ bib: bib, lap: lap });
    app.bibs_clocked_here.length = Math.min(10, app.bibs_clocked_here.length);
  });

  return false;
};
