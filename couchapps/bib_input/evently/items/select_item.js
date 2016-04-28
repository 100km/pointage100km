function(e, data) {
  // Get the matching <li> element according to the data.
  var li = $('#js_checkpoint_' + data.bib + '_' + data.lap);

  // Update the data of the selected_item to be up to date.
  $('#items').data('selected_item', data);

  // Unselect everybody and select the one we find.
  li.parents("ul").children().removeClass('selected');
  li.addClass('selected');

  // Update the ts of the data with the ts of the line (comes from DB).
  data.ts = parseInt(li.find('input[name="ts"]').val());

  // Trigger the change_infos event to update the #previous widget.
  li.trigger("change_infos", data);
}
