// View : all_contestants
// Used to retrive the infos for the constestants, the goal being retrieving all the constestants at once
// Used in ranking_title_div
//TODO teams
function(doc) {
  if (doc.type == 'contestant' ) {
      emit(doc.bib, {name: doc.name, first_name: doc.first_name, bib: doc.bib,
        birth: doc.birth, handisport: doc.handisport,
        championship: doc.championship || false, zipcode: doc.zipcode,
        sex: doc.sex, race: doc.race, city: doc.city});
  }
};
