function(doc) {
  if (doc.bib != undefined) {
    if (doc.first_name)
    for (prop in {"prenom":"", "nom":""}) {
      if (doc[prop]) {
        emit(doc[prop], {
          match: prop,
          first_name: doc.first_name || "",
          name: doc.name || "",
          bib: doc.bib
        });
      }
    }
  }
};
