function(data) {
  var i = 0;
  var current_bib = 0;
  var times = [[], [], []];
  var res = { pbs: [], times_site0: '', times_site1: '', times_site2: ''};
  var pings = [data[0][0]['max'], data[1][0]['max'], data[2][0]['max']];

  while (data[3][0].rows[i]) {
    var row = data[3][0].rows[i];
    var bib = row.key[0];

    // we can now check for previous bib
    if (bib != current_bib) {
      // Do the check.
      do_check_times(res, current_bib, times, pings);

      // Update current bib for the next check.
      current_bib = bib;
    }

    // Just keep the info for all rows
    times[row.value.site_id] = row.value.times;

    i++;
  }

  // Do the check for the last bib.
  do_check_times(res, current_bib, times, pings);


  // Return the data.
  return res;
};

/**
 * Do the check for the given matrix of times.
 * @param res: store the result of the action.
 * @param bib: the bib that we check.
 * @param times: matrix of times for each site.
 * @param pings: the pings for each site.
 */
function do_check_times(res, bib, times, pings) {
  // Check the times.
  var check = check_times(times, pings);

  // There was a problem.
  if (check) {
    check.bib = bib;
    res.pbs.push(check)
  }

  // Display the times for each site.
  for (var i = 0; i < times.length; i++) {
    if (check) {
      check['times_site' + i] = (times[i] || []).map(function(t) { return { val: format_date(new Date(t)) }; });
    }

    // And we empty times[i] for next bib check
    times[i] = [];
  }
}

function format_date(date) {
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

