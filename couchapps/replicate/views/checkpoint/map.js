function(doc) {
  // FIXME: check for doc.type == "checkpoint" when issue #102 is fixed
  if (doc.bib && doc.site_id != undefined && doc.race_id != undefined && doc.times != undefined && !doc._deleted)
    emit(doc.bib, doc);
}
