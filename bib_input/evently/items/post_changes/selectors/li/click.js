function() {
  var $_arrow=$("#arrow");
  var $_this=$(this);
  var pos = $_this.offset();
  var x_offset = $_this.width();
  var y_offset = ($_this.height()-42)/2+4; // +4 because of the border
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;
  $_arrow.css({ position: "absolute",
                marginLeft: 0, marginTop: 0,
                top: pos.top, left: pos.left });

  // First clear all lines
  $(this).parents("ul").children().children().css("font-weight", "");
  $(this).parents("ul").children().css("background-color","white");
  // Then set the clicked lines to bold
  $(this).children().css("font-weight", "bold");
  $(this).css("background-color", "#c0ffc0");

  $_arrow.show();
}
