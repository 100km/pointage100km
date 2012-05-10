function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined) {
    return {
      item_0 : [],
      items : [],
    }
  }

  // Return the data to display on item line.
  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    p.time_hour = time_to_hour_string(p.ts);

    p.nom = r.infos && r.infos.nom;
    p.prenom = r.infos && r.infos.prenom;
    p.course = r.infos && r.infos.course;

    return p;
  }

  return {
    items : data.map(create_infos)
  }

};
