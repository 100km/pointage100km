// View : all-times-per-bib-ng
// Used to display the times of a contestant (search)
//TODO teams
function(doc) {
  if (doc.type === "analysis") {
    var after = doc.after;
    for (var i = 0; i < after.length; i++)
      emit([doc.bib, after[i].lap, after[i].site_id], after[i].time);
  }
}
