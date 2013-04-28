// View : teams
// Used to show the team members of a team
// Used in team_global_ranking
function(doc) {
  if (doc.type == 'contestant' && doc.team_id != undefined) {
      emit(doc.team_id, doc);
  }
};
