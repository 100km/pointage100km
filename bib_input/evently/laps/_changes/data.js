function(data) {
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.bib = r.key;
      p.lap = r.value && r.value.times && r.value.times.length;
      return p;
    })
  }
};
