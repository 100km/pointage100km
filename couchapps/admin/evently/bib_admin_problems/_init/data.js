function(data) {
  var i = 0;
  var current_bib = 0;
  var times = [[], [], []];
  var res = {};
  res.pbs = [];
  var pb_nb = 0;
  var ping0 = data[0][0]['max'];
  var ping1 = data[1][0]['max'];
  var ping2 = data[2][0]['max'];

  while (data[3][0].rows[i]) {
    var row = data[3][0].rows[i];
    var bib = row.key[0];

    if (bib != current_bib) {

      // we can now check for previous bib
      res.pbs[pb_nb] = check_times(times, ping0, ping1, ping2);
      // there was a problem
      if (res.pbs[pb_nb].site_id != undefined) {
        res.pbs[pb_nb].bib = current_bib;
        pb_nb ++;
      }

      // and we empty times
      times[0] = [];
      times[1] = [];
      times[2] = [];

      current_bib = bib;
    }

    // Just keep the info for all rows
    times[row.value.site_id] = row.value.times;

    i++;
  }

  // Check the last bib
  res.pbs[pb_nb] = check_times(times, ping0, ping1, ping2);
  // there was a problem
  if (res.pbs[pb_nb].site_id != undefined) {
    res.pbs[pb_nb].bib = current_bib;
    pb_nb ++;
  }

  // Remove the last element (which is empty)
  res.pbs.pop();

  return res;
};
