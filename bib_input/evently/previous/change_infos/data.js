function(data) {
  var app = $$(this).app;

  data.current_bib = app.current_bib;
  data.nom = data.infos.nom
  data.prenom = data.infos.prenom

  return data;
};
