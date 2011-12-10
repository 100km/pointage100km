function(data) {
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.bib = r.value && r.value.bib;
      p.lap = r.value && r.value.lap;
      p.ts  = r.key;
      date = new Date(r.key)
      p.time_day = date.getDate() + "/" + (date.getMonth()+1) + "/" + date.getFullYear();
      p.time_hour = date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds();
      return p;
    })
  }
};
