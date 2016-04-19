function(doc) {
  if (doc.type === "ping")
    emit(doc.site_id, doc.time);
  else if (doc.type === "checkpoint") {
    var artificial_times = doc.artificial_times || [];
    var i = doc.times.length - 1;
    while (i >= 0) {
      if (artificial_times.indexOf(doc.times[i]) === -1) {
        emit(doc.site_id, doc.times[i]);
        break;
      }
      i--;
    }
  }
}
