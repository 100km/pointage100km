function(data) {
  var app = $$(this).app;

  // Set field in app so that everyone can access the contestants infos
  app.contestants = data;

  $(this).trigger("update_race_number_1");
  $(this).trigger("update_race_number_2");

  return;
};
