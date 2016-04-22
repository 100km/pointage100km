// View : global-ranking
// Used for global ranking of all contestants (to be diplayed in the web site for example)
//TODO teams
function(doc) {
  if (doc.type === "analysis" && doc.after.length > 0) {
    var last = doc.after[doc.after.length - 1];
    var synthesized = {bib: doc.bib, race_id: doc.race_id, site_id: last.site_id,
      times: [last.time]};
    emit([doc.race_id, -last.lap, -last.site_id, last.time], synthesized);
  }
};
