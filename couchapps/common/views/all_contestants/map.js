// View : all_contestants
// Used to retrive the infos for the constestants, the goal being retrieving all the constestants at once
// Used in ranking_title_div
//TODO teams
function(doc) {
  if (doc.type == 'contestant' ) {
      emit(doc.bib, doc);
  }
};
