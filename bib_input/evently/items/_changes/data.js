function(data) {
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.bib = r.value && r.value.bib;
      p.lap = r.value && r.value.lap;
      return p;
    })
  }
};
