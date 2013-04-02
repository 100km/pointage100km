//Unused ?
//TODO teams
function(doc) {
  if (doc._id == "infos")
    emit(false, doc);

  if (doc.bib!=undefined && doc.times && doc.site_id!=undefined) {
    emit([doc.bib, doc.site_id, false], {_id:"contestant-" + doc.bib});

    emit([doc.bib, doc.site_id, true], doc);
  }
}
