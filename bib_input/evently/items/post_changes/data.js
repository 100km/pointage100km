function(data) {
  var p;
  var app = $$(this).app;

  if (data[0].value && (data[0].value.bib != app.current_bib) ) {
    app.current_li = $($("#items").find("li")[1]); // 1 because 0 is the table's title
    place_arrow($($("#items").find("li")[1]));
    app.current_bib = data[0].value.bib
    $(this).trigger("change_infos");
  }

  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    date = new Date(p.ts)
    p.time_day = date.getDate() + "/" + (date.getMonth()+1) + "/" + date.getFullYear();
    p.time_hour = date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds();

    p.nom = r.infos && r.infos.nom;
    p.prenom = r.infos && r.infos.prenom;
    p.course = r.infos && r.infos.course;

    return p;
  }

  // separate first element from others
  return {
    item_0 : create_infos(data.shift()),
    items : data.map(create_infos)
  }
};
