function(doc) {
  if (doc.type === "checkpoint" && doc.times)
    for (var i = 0; i < doc.times.length; i++)
      emit([doc.site_id, -doc.times[i]], doc.bib);
}
