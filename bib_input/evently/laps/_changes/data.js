function(data) {
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.bib = r.key;
      p.lap = r.value;
      return p;
    })
  }
};
