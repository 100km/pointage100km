function(head, req) {

  start({
    "headers": {
      "Content-Type": "text/plain"
    }
  });

  function getBibRow() {
    var res;
    var first = getRow();
    if (first) {
      var second = getRow();
      res = { checkpoint: first.doc, info: second.doc };
    }
    return res;
  }

  var races_hash = {};
  var set = {};
  var current_race_id;
  var row;
  while (row=getBibRow()) {
    var bib = row.checkpoint.bib;
    var race_id = row.info.course;
    if (!set[bib]) {
      set[bib] = true;
      if (!races_hash[race_id]) {
        races_hash[race_id] = [];
      }
      races_hash[race_id].push(row);
    }
  }

  var races = [];
  for (var race_id in races_hash) {
     races.push({race_id: Number(race_id), contestants: races_hash[race_id]});
  }
  return JSON.stringify({rows: races, total_rows: races.length});
}
