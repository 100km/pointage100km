function(e) {
  var data = {};
  data.value = {
    nom: this.getAttribute('data-name'),
    prenom: this.getAttribute('data-firstname'),
    dossard: parseInt(this.getAttribute('data-bib'))
  }
  $(this).trigger("single-contestant", data);
}
