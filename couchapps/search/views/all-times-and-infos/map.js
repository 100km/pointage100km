//Use with search/lists/times-to-csv.js to export times in sql with include_docs=true:
// <host/db>/_design/search/_list/times-to-csv/all-times-and-infos?include_docs=true
//TODO teams
function(doc) {
  if (doc._id == "infos")
    emit(false, doc);

  if (doc.bib!=undefined && doc.times && doc.site_id!=undefined) {
    emit([doc.bib, doc.site_id, false], {_id:"contestant-" + doc.bib});

    emit([doc.bib, doc.site_id, true], doc);
  }
}
