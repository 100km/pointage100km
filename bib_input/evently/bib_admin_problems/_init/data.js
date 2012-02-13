function(data) {
  var i = 0;
  var current_bib = 0;
  var times = [[], [], []];
  var res = {};
  res.pbs = [];
  var pb_nb = 0;
  $.log("In data.js for bib_problems");
  $.log(JSON.stringify(data));

  function check_times(times) {
    var j = 0;

    $.log("In check_times " + JSON.stringify(times));
    while (times[j%3][parseInt(j/3)]) {
      if (times[(j+1)%3][parseInt((j+1)/3)] < times[j%3][parseInt(j/3)]) {
        res.pbs[pb_nb] = {};
        res.pbs[pb_nb].site_id = 0;
        res.pbs[pb_nb].type = "Manque un passage";
        res.pbs[pb_nb].lap = j;
        res.pbs[pb_nb].bib = current_bib;
        res.pbs[pb_nb].times_site0 = JSON.stringify(times[0]);
        res.pbs[pb_nb].times_site1 = JSON.stringify(times[1]);
        res.pbs[pb_nb].times_site2 = JSON.stringify(times[2]);

        pb_nb ++;
      }
      j++;
    }
  }

  while (data.rows[i]) {
    var row = data.rows[i];
    var bib = row.key[0];

    if (bib != current_bib) {
      $.log("Processing bib " + current_bib);

      // we can now check for previous bib
      check_times(times);

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
  $.log("Processing bib " + current_bib);
  check_times(times);

  return res;
};
