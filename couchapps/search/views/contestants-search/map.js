//Used in search
//TODO teams
function(doc) {
  if (doc.bib != undefined) {
    if (doc.first_name)
    for (prop in {"first_name":"", "name":""}) {
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
