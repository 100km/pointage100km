function(data) {
  var i = 0;
  var current_bib = 0;
  var times = [];
  var res = { pbs: [] };
  var pings = [data[0][0], data[1][0], data[2][0]];

  while (data[3][0].rows[i]) {
    var row = data[3][0].rows[i];
    var bib = row.key[0];

    // we can now check for previous bib
    if (bib != current_bib) {
      // Do the check.
      do_check_times(res, current_bib, times, pings);

      // Empty the times table for next bib check.
      times = [];

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
    // Push the check object into the list of problems.
    res.pbs.push(check)

    // Set the bib for this check.
    check.bib = bib;

    // Display the times for each site.
    check.sites = [];

    for (var i = 0; i < Math.min(times.length, pings.length); i++) {
      // Build the list of times of this site and bib.
      var site_bib_times = [];

      // Check if there is a times list for this site (other wise it means there is no info from this site).
      if (times[i]) {

        // Enumerate for each lap on the current site times.
        for (var lap = 0; lap < times[i].length; lap++) {

          // Check if the error detection has flag the current lap postion as missing.
          if (check.type == 'Manque un passage' && check.lap == (lap + 1) && check.site_id == i) {
            site_bib_times.push({ add: true, lap: lap, bib: bib, site_id: i });
          }

          // Get the time value and add a line (also check if this time was detected as wrongly inserted).
          var t = times[i][lap];
          site_bib_times.push({
            val: format_date(new Date(t)),
            lap: lap,
            bib: bib,
            site_id: i,
            remove: check.type == 'Passage supplémentaire' && check.lap == (lap + 1) && check.site_id == i,
          });
        }
      } else if (check.type == 'Manque un passage' && check.lap == 1 && check.site_id == i) {
        // There is no data for this site and the error detection say it misses the first lap.
        site_bib_times.push({ add: true, lap: 0, bib: bib, site_id: i });
      }

      // Push the site object definition.
      check.sites.push({
        id: i,
        bib: bib,
        times: site_bib_times,
      });
    }
  }
}

function format_date(date) {
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

