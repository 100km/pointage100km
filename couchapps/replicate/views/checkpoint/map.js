function(doc) {
  if (doc.type == "checkpoint" && doc.bib && doc.site_id && doc.race_id != undefined && doc.times != undefined)
    emit(doc.bib, doc);
}
