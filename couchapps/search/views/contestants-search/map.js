function(doc) {
  if (doc.prenom && doc.dossard) {
    emit(doc.prenom, doc.dossard);
  }
  if (doc.nom && doc.dossard) {
    emit(doc.nom, doc.dossard);
  }
}
