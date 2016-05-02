function(e) {
  e.preventDefault();

  var form = this;
  var bib = form.bib.value;
  var ts = form.ts.value;
  if (bib == '' || ts == '') {
    return false;
  }

  bib = parseInt(bib);
  ts = parseInt(ts);

  // Start loading the image that will replace the delete input.
  var img = $('<img/>');
  img.unbind('load').load(function() {
    // Remove the delete button of the form.
    $(form).find('input[type="image"]').remove()

    // Add the image to the form.
    $(form).prepend(img);

    // Send the request to delete the bib.
    remove_checkpoint(bib, $$(form).app, ts);

    $("#previous").trigger('clear_infos');
  }).get(0).src = 'img/loading.gif';

  return false;
};
