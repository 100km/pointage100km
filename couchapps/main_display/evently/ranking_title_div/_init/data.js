function(data) {
  // Set field in app so that everyone can access the site_id
  var app = $$(this).app;

  $.log("received data: " + JSON.stringify(data));

  app.contestants = data;

  $(this).trigger("update_race_number_1");
  $(this).trigger("update_race_number_2");

  // Set title for the document
  document.title = "Classement2";

  return;
};
