function(data) {
  var p;

  return {
    races: data.rows.map(function(pair) {
      return {
        race_id: pair.race_id,
        items: pair.contestants.map(function(r) {
          p = {};
          p.bib = r.value && r.value.bib;
          p.lap = r.value && r.value.times.length;
          return p;
        })
      }
    })
  }
};
