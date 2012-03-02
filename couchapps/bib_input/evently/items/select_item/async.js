function(callback, e, data) {
  // Get the matching <li> element according to the data.
  var selector_bib = 'li:has(form#delete input[name="bib"][value="' + data.bib + '"])';
  var selector_lap = 'li:has(input[name="lap"][value="' + data.lap + '"])';
  var li = $(selector_bib).filter(selector_lap);

  // Update the data of the selected_item to be up to date.
  $('#items').data('selected_item', data);

  // Unselect everybody and select the one we one.
  li.parents("ul").children().removeClass('selected');
  li.addClass('selected');

  // Update the ts of the data with the ts of the line (comes from DB).
  data.ts = li.find('input[name="ts"]').val();

  // Trigger the change_infos event to update the #previous widget.
  li.trigger("change_infos", data);
}
