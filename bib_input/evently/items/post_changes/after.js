function () {
  // Get the correct li for the dom.
  var app = $$(this).app;
  var selector_bib = 'li:has(form#delete input[name="bib"][value="' + app.current_bib + '"])';
  var selector_lap = 'li:has(input[name="lap"][value="' + app.current_lap + '"])';
  app.current_li = $(selector_bib).filter(selector_lap);

  // First clear all lines
  app.current_li.parents("ul").children().children().css("font-weight", "");
  app.current_li.parents("ul").children().css("background-color","white");
  // Then set the clicked lines to bold
  app.current_li.children().css("font-weight", "bold");
  app.current_li.css("background-color", "#d0ffd0");
}
