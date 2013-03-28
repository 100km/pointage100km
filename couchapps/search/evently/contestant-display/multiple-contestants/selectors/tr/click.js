function(e) {
  $(this).trigger("single-contestant", {
    name: this.getAttribute('data-name'),
    first_name: this.getAttribute('data-firstname'),
    bib: parseInt(this.getAttribute('data-bib'))
  });
}
