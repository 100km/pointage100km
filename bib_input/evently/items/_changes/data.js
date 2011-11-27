function(data) {
  // $.log(data)
  var p;
  return {
    items : data.rows.map(function(r) {
      p = {};
      p.message = r.value && r.value.message;
      return p;
    })
  }
};
