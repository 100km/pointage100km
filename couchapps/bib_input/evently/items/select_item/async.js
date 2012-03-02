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

  // Keep this, current bib and current lap into app
  $$(this).app.current_li = li;
  $$(this).app.current_bib = data.bib;
  $$(this).app.current_lap = data.lap;
  $$(this).app.current_ts = data.ts;

  li.trigger("change_infos");
}
