function(doc) {
  if (doc.type == "checkpoint" && doc.times) {
    var times = doc.times.concat(doc.deleted_times || []);
    for (var i in times) {
      emit([doc.site_id, -times[i], doc._id, 0]);
      emit([doc.site_id, -times[i], doc._id, 1], { _id: "contestant-" + doc.bib });
    }
  }
}
