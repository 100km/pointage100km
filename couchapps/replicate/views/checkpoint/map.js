function(doc) {
  if (doc.type == "checkpoint" && doc.bib && doc.site_id != undefined && doc.race_id != undefined && doc.times != undefined)
    emit(doc.bib, doc);
}
