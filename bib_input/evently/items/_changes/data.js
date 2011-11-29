function(data) {
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.bib = r.value && r.value.bib;
      return p;
    })
  }
};
