function() {
  var _this = this;
  $(this).find("#ranking-container").vTicker({
    speed: 500,
    pause: 1000,
    animation: 'fade',
    mousePause: false,
    showItems: 10
  }, function() {
    setTimeout( function() {
      $(_this).trigger("update_ranking");
    }, 2000);
  });
}
