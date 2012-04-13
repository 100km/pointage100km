// View : bib_info
// Used to get the info of last contestants displayed in "items" div
function(doc) {
  if (doc.dossard != undefined) {
    emit(doc.dossard, doc);
  }
};
