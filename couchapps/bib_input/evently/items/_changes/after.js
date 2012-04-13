function (datas) {
  // Get the selected_item from the data of the widget.
  // This is set by the call make of the bib_input submit because we don't
  // a direct callback chain from the input to here, because this is the data base that trigger this event.
  var selected_item = $(this).data('selected_item');

  $.each(datas, function(index, info) {
    // Build the data object for the checkpoint.
    var data;
    if (info.doc) {
      data = info.doc;
    } else {
      data = { warning: true };
    }
    data.bib = info.value.bib;
    data.lap = info.value.lap;
    // Store information of each contestant, to avoid to retrieve them again from DB when you click on the element.
    $('#js_checkpoint_' + info.value.bib + '_' + info.value.lap).data('checkpoint', data);
    // If the selected item is matching, update with more data about the checkpoint.
    if (selected_item && selected_item.bib == data.bib && selected_item.lap == data.lap) {
      selected_item = data
    }
  });

  // There is not selected item in the data of the widget, select the first one.
  if (!selected_item) {
    var li = $(this).find('li:eq(1)');
    selected_item = li.data('checkpoint');
  }

  // Trigger 'select_item' with the selected_item
  if (selected_item) {
    $(this).trigger('select_item', selected_item);
  }
}
