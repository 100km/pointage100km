function() {
  var bib = parseInt($(this).find('input[name="bib"]').val());
  var lap = parseInt($(this).find('input[name="lap"]').val());

  $(this).trigger('select_item', { bib: bib, lap: lap });
}
