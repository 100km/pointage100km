function(data) {
  var app = $$(this).app;

  data.current_bib = app.current_bib;
  data.current_lap = app.current_lap;
  data.nom = data.infos.nom
  data.prenom = data.infos.prenom
  data.adresse = data.infos.adresse;
  data.cp = data.infos.cp;
  data.commune = data.infos.commune;

  return data;
};
