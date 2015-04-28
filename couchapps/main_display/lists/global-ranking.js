function(head, req) {

  start({
    "headers": {
      "Content-Type": "application/json"
    }
  });

  var res = {};
  var races = [];
  var arr = [];
  var set = {};
  var current_race_id;
  var row = getRow();
  while (row) {
    current_race_id = row["value"]["race_id"];
    do {
      var tmp_bib = row["value"]["bib"];
      var tmp_race_id = row["value"]["race_id"];
      if (tmp_race_id!=current_race_id) {
        break;
      }
      if (!set[tmp_bib]) {
        set[tmp_bib] = true;
        arr.push(row);
      }
    } while (row = getRow())

    pair = {};
    pair.race_id = current_race_id;
    pair.contestants = arr;
    races.push(pair);
    arr = [];
  }
  res.rows = races;
  res.total_rows = races.length;
  return JSON.stringify(res);
}
