//Used in search
//TODO teams
function(doc) {
  if (doc.bib!=undefined && doc.times && doc.site_id!=undefined)
    emit(doc.bib, doc);
}
