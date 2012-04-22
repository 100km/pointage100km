function(data) {
  var p = {};
  p.items = [];
  var i = 0;
  var app = $$(this).app;

  while (data.rows[0].contestants[i]) {
    var item = {};
    var current_infos = data.rows[0].contestants[i].value;
    var current_contestant = app.contestants[current_infos.bib];

    //$.log("current_infos = " + JSON.stringify(current_infos));
    if (current_contestant === undefined) {
      $.log("skip contestant: " + data.bib);
    }

    item.rank = i;
    item.dossard = current_infos.bib;
    item.lap = current_infos.lap
    item.nom     = current_contestant.nom;
    item.prenom  = current_contestant.prenom;
    item.course  = current_contestant.course;

    p.items.push(item);
    i++;
  }

  p.count = i;

  $.log("p: " + JSON.stringify(p));

  return p;
};
