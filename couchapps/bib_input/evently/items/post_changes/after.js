function () {
  // Get the selected_item from the data of the widget.
  // This is set by the call make of the bib_input submit because we don't
  // a direct callback chain from the input to here, because this is the data base that trigger this event.
  var selected_item = $(this).data('selected_item');

  // There is not selected item in the data of the widget, select the first one.
  if (!selected_item) {
    var first_line = $(this).find('li:eq(1)');
    var bib = first_line.find('input[name="bib"]').val();
    var lap = first_line.find('input[name="lap"]').val();
    if (bib && lap) {
      selected_item = { bib : bib, lap: lap };
    }
  }

  // Trigger 'select_item' with the selected_item
  if (selected_item) {
    $(this).trigger('select_item', selected_item);
  }
}
