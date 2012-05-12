function() {
  var _this = this;
  $(_this).trigger("tick")
  setInterval(function() {
    $(_this).trigger("tick")
  }, 1000);
}
