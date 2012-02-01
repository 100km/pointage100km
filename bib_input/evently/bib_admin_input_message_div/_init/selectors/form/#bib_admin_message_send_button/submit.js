function() {

// THIS IS NOT THE CODE THAT IS BEING CALLED!!

    var form = $(this).parent("form");
    var message = form["bib_admin_message"].value; 
    var message_target = form["bib_admin_message_target"].value;

    $.log("2 message " + message + " target " + message_target);

// we are sure it's an integer because of the regexp check.
  //if (! isBib(bib)) return false;
  //bib = parseInt(bib, 10);
    form.reset();

  //var app = $$(this).app;
  //app.current_bib = 0;
  //app.current_lap = 0;

//  submit_bib(bib, app);
    return false;

/*    var bib = form["bib"].value;
    var lap = form["lap"].value;
  var ts = form["ts"].value;
  if (bib == "" || ts == "" || lap == "" ) return false;
  bib = parseInt(bib);
  lap = parseInt(lap);

  var app = $$(this).app;
  if ((bib == app.current_bib) && (lap == app.current_lap)) {
    // $.log("ERASING current_li");
    app.current_li = null;
    app.current_bib = 0;
    app.current_lap = 0;
  }

  $(this).parents("li").hide('fast', function() {
      submit_remove_checkpoint(bib, app, ts);
  });
  return false;
*/
};
