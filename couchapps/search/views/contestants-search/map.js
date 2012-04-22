function(doc) {
  if (doc.dossard != undefined) {
    if (doc.prenom)
    for (prop in {"prenom":"", "nom":""}) {
      if (doc[prop]) {
        emit(doc[prop], {
          match: prop,
          prenom: doc.prenom || "",
          nom: doc.nom || "",
          dossard: doc.dossard
        });
      }
    }
  }
};
