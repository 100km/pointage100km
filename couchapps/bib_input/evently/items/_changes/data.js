function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined)
    return {
      item_0 : [], //TODO unused?
      items : [],
    }

  // Return the data to display on item line.
  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    p.time_hour = time_to_hour_string(p.ts);

    p.name       = r.infos && r.infos.name;
    p.first_name = r.infos && r.infos.first_name;
    p.race       = r.infos && r.infos.race;

    //This is findWhere but our underscore.js is to old..
    p.added_elsewhere = r.value && r.value.bib && r.value.lap && !_.find(app.bibs_clocked_here, function(elem) {
      return elem.bib === r.value.bib && elem.lap === r.value.lap;
    });

    return p;
  }

  return {
    items : data.map(create_infos)
  }
};
