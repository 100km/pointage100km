function(data) {
  var _this = this;

  if (data.race_id == 3)
    $(_this).addClass('race_3');
  else
    $(_this).removeClass('race_3');

  $(this).find("#ranking-container").vTicker({
    speed: 500,
    pause: 1000,
    animation: 'fade',
    mousePause: false,
    showItems: 10
  }, function() {
    setTimeout(function() {
      $(_this).trigger("_init");
    }, 1000);
  });
}
