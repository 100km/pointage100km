function(data) {
  var i = 0;
  var current_bib = 0;
  var times = [];
  var deleted_times = [];
  var res = { pbs: [] };
  var pings = [data[0][0], data[1][0], data[2][0]];

  while (data[3][0].rows[i]) {
    var row = data[3][0].rows[i];
    var bib = row.key[0];

    // we can now check for previous bib
    if (bib != current_bib) {
      // Do the check.
      do_check_times(res, current_bib, times, deleted_times, pings);

      // Empty the times table for next bib check.
      times = [];
      deleted_times = [];

      // Update current bib for the next check.
      current_bib = bib;
    }

    // Just keep the info for all rows
    times[row.value.site_id] = row.value.times;
    deleted_times[row.value.site_id] = row.value.deleted_times;

    i++;
  }

  // Do the check for the last bib.
  do_check_times(res, current_bib, times, deleted_times, pings);

  // Return the data.
  return res;
};

/**
 * Do the check for the given matrix of times.
 * @param res: store the result of the action.
 * @param bib: the bib that we check.
 * @param times: matrix of times for each site.
 * @param deleted_times: the deleted times for this site.
 * @param pings: the pings for each site.
 */
function do_check_times(res, bib, times, deleted_times, pings) {
  // Check the times.
  // NOTE(bapt): hard code the number of site...
  var check = check_times(times, pings, 3);

  // There was a problem.
  if (check) {
    // Push the check object into the list of problems.
    res.pbs.push(check)

    // Set the bib for this check.
    check.bib = bib;

    // Display the times for each site.
    check.sites = [];

    // NOTE(bapt): Hard code site number.
    for (var i = 0; i < 3; i++) {
      // Push the site object definition.
      check.sites.push({
        id: i,
        bib: bib,
        times: get_site_bib_times(check, times, i, bib),
        deleted_times: get_site_bib_deleted_times(deleted_times, i, bib),
      });
    }
  }
}

/**
 * Get the deleted times for the site and bib.
 * @param deleted_times: the list of all deleted times (for all sites).
 * @param i: the site id.
 * @param bib: the current bib.
 */
function get_site_bib_deleted_times(deleted_times, i, bib) {
  var site_bib_deleted_times = [];

  if (deleted_times[i]) {
    for (var j = 0; j < deleted_times[i].length; j++) {
      site_bib_deleted_times.push({
        val: format_date(new Date(deleted_times[i][j])),
        lap: j,
        bib: bib,
        site_id: i
      });
    }
  }

  return site_bib_deleted_times;
}


/**
 * Get the times for the site and the bib.
 * @param check: the check object will hold the data passed to the mustache element.
 * @param times: the matrix of times (each line is a site and each column a lap).
 * @param i: the id of the site.
 * @param bib: the current bib.
 */
function get_site_bib_times(check, times, i, bib) {
  // Build the list of times of this site and bib.
  var site_bib_times = [];

  // Check if there is a times list for this site (other wise it means there is no info from this site).
  if (times[i] && times[i].length > 0) {

    // Enumerate for each lap on the current site times.
    for (var lap = 0; lap < times[i].length; lap++) {

      // Check if the error detection has flag the current lap postion as missing.
      if (check.type == 'Manque un passage' && check.lap == (lap + 1) && check.site_id == i) {
        site_bib_times.push({
          add: true,
          lap: lap,
          bib: bib,
          site_id: i,
          next_site: check.next_site,
          next_time: check.next_time,
          prev_site: check.prev_site,
          prev_time: check.prev_time,
        });
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
  }

  // Add missing button that are outside range of times[i] array.
  if (check.type == 'Manque un passage' && check.site_id == i
    && (((!times[i] || times[i].length == 0) && check.lap == 1)
      || check.lap > times[i].length)) {
    // There is no data for this site and the error detection say it misses the lap.
    site_bib_times.push({
      add: true,
      lap: times[i] ? times[i].length : 0,
      bib: bib,
      site_id: i,
      next_site: check.next_site,
      next_time: check.next_time,
      prev_site: check.prev_site,
      prev_time: check.prev_time,
    });
  }

  return site_bib_times;
}

