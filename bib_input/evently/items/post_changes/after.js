function () {
  function is_app_current_li_valid(app) {
    var form=app.current_li.find("#delete")[0];
    return (parseInt(form["bib"].value) == app.current_bib &&
            parseInt(form["lap"].value) == app.current_lap);
  }

  var app = $$(this).app;

  // Here we ensure the arrow is still at the right place
  for (var i=1; i<=50; i++) { // starting at 1 because 0 is the table's title
    app.current_li = $($("#items").find("li")[i]);
    if (is_app_current_li_valid(app))
      break;
  }

  // First clear all lines
  app.current_li.parents("ul").children().children().css("font-weight", "");
  app.current_li.parents("ul").children().css("background-color","white");
  // Then set the clicked lines to bold
  app.current_li.children().css("font-weight", "bold");
  app.current_li.css("background-color", "#d0ffd0");

  place_arrow(app.current_li);
}
