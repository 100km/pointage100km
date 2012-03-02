function() {
  var bib = $(this).find('input[name="bib"]').val();
  var lap = $(this).find('input[name="lap"]').val();
  var ts = $(this).find('input[name="ts"]').val();

  $(this).trigger('select_item', { bib: bib, lap: lap, ts: ts });
}
