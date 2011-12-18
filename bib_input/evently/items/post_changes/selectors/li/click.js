function() {
  var app = $$(this).app;
  var $_arrow=$("#arrow");
  var $_this=$(this);
  var pos = $_this.offset();
  var x_offset = $_this.width();
  var y_offset = ($_this.height()-42)/2+4; // +4 because of the border
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;

  // Set the div to link concurrent with its infos
  $_arrow.css({ position: "absolute",
                marginLeft: 0, marginTop: 0,
                top: pos.top, left: pos.left });
  $_arrow.show();

  // First clear all lines
  $_this.parents("ul").children().children().css("font-weight", "");
  $_this.parents("ul").children().css("background-color","white");
  // Then set the clicked lines to bold
  $_this.children().css("font-weight", "bold");
  $_this.css("background-color", "#d0ffd0");

  // Keep this and current bib into app
  app.current_li = $_this;
  app.current_bib = $_this.find("#delete")[0]["bib"]["value"];
}
