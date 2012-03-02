function(e) {
  e.preventDefault();

  var form = this;
  var bib = form.bib.value;
  var lap = form.bib.value;
  var ts = form.ts.value;
  if (bib == '' || ts == '' || lap == '') {
    return false;
  }

  bib = parseInt(bib);
  lap = parseInt(lap);

  // Start loading the image that will replace the delete input.
  var img = $('<img/>');
  img.unbind('load').load(function() {
    // Remove the delete button of the form.
    $(form).find('input[type="image"]').remove()

    // Add the image to the form.
    $(form).prepend(img);

    // If it was the select line, clear the infos and the selected_item.
    if ($(form).parents('li').hasClass('selected')) {
      $(form).trigger('set_selected_item', null);
      $(form).trigger('clear_infos');
    }

    // Send the request to delete the bib.
    submit_remove_checkpoint(bib, $$(form).app, ts);
  }).get(0).src = 'img/loading.gif';

  return false;
};
