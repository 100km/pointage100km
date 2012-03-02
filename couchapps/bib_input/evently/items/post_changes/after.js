function () {
  // Get the selected_item from the data of the widget.
  // This is set by the call make of the bib_input submit because we don't
  // a direct callback chain from the input to here, because this is the data base that trigger this event.
  var selected_item = $(this).data('selected_item');

  // There is not selected item in the data of the widget, select the first one.
  // TODO(bapt): consider to move this part in a upper part of the application, this logic should no be here.
  // because it concerns when the user first come to the page.
  if (!selected_item) {
    var first_line = $(this).find('li:eq(1)');
    var bib = first_line.find('input[name="bib"]').val();
    var lap = first_line.find('input[name="lap"]').val();
    if (bib && lap) {
      selected_item = { bib : bib, lap: lap };
      $(this).data('selected_item', selected_item);
    }
  }

  // Trigger 'select_item' with the selected_item
  if (selected_item) {
    $(this).trigger('select_item', selected_item);
  }
}
