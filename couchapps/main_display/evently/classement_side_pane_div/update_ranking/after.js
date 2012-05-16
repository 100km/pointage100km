function(data) {
  var _this = this;

  if (data.race_id == 4)
    $(_this).addClass('race_4');
  else
    $(_this).removeClass('race_4');

  $(this).find("#ranking-container").vTicker({
    speed: 500,
    pause: 1000,
    animation: 'fade',
    mousePause: false,
    showItems: 10
  }, function() {
    setTimeout(function() {
      $(_this).trigger("update_ranking");
    }, 1000);
  });
}
