function(callback, e, data) {
  $.log('select item is called, data =');
  $.log(data);

  // Get the matching <li> element.
  var selector_bib = 'li:has(form#delete input[name="bib"][value="' + data.bib + '"])';
  var selector_lap = 'li:has(input[name="lap"][value="' + data.lap + '"])';
  var li = $(selector_bib).filter(selector_lap);

  // First clear all lines
  li.parents("ul").children().removeClass('selected');
  // Then set the clicked lines to bold
  li.addClass('selected');

  // Trigger the change_infos event to update the previous panel
  li.trigger("change_infos", data);
}
