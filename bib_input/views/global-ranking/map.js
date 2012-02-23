// View : global-ranking
// Used for global ranking of all contestants (to be diplayed in the web site for example)
// Must be used with include_docs = true to have the documents
function(doc) {
  if (doc.bib != undefined && doc.times && doc.times.length > 0 && doc.site_id != undefined) {
    var len = doc.times.length;
      emit([1, -len, -doc.site_id, doc.times[len-1], 0], null);
      emit([1, -len, -doc.site_id, doc.times[len-1], 1], {_id:"contestant-"+doc.bib}); //TODO use infos_id function
  }
};
