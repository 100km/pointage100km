function(data) {
  var p;
  var app = $$(this).app;

  return {
    items : data.map(function(r) {
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
    })
  }
};
