function() {
  var _this = this;
  $(_this).trigger("tick")
  //see http://bugs.jquery.com/ticket/9678
  var dummy_interval = setInterval(function () {
  }, 100000);
  setInterval(function() {
    $(_this).trigger("tick")
  }, 1000);
}
