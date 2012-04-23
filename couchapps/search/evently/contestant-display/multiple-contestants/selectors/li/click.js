function(e) {
  $(this).trigger("single-contestant", {
    nom: this.getAttribute('data-name'),
    prenom: this.getAttribute('data-firstname'),
    dossard: parseInt(this.getAttribute('data-bib'))
  });
}
