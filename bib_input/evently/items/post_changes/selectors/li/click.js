function() {
  var $_arrow=$("#arrow");
  var $_this=$(this);
  var pos = $_this.offset();
  var x_offset = $_this.width();
  var y_offset = $_this.height()/4;
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;
  $_arrow.offset(pos);
  $_arrow.show();
}
