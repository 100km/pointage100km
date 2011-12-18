function() {
  var $_arrow=$("#arrow");
  var $_this=$(this);
  var pos = $_this.offset();
  var x_offset = $_this.width();
  var y_offset = ($_this.height()-30)/4;
  $.log("In on click pos : " + JSON.stringify(pos) + "yoffset " + x_offset);
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;
  $_arrow.css({ position: "absolute",
                marginLeft: 0, marginTop: 0,
                top: pos.top, left: pos.left });
  $_arrow.show();
}
