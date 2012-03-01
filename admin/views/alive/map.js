function(doc) {
    if (doc.site_id != undefined) {
	if (doc.time)
	    emit(doc.site_id, time);
	else if (doc.times && doc.times.length > 0)
	    emit(doc.site_id, doc.times[doc.times.length - 1]);
    }
}
