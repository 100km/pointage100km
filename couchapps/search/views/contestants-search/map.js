//Used in search
//TODO teams
function(doc) {
  if (doc.bib != undefined) {
    if (doc.first_name)
    for (prop in {"first_name":"", "name":"", "bib":""}) {
      if (doc[prop]) {
        emit(doc[prop], {
          match: prop,
          first_name: doc.first_name || "",
          name: doc.name || "",
          race: doc.race,
          bib: doc.bib
        });
      }
    }
  }
};
