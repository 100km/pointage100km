function(head, req) {

function format_date(date) {
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

// lcs from sp4ce
var lcs_DELETION;
var lcs_RIGHT;
var lcs_INSERTION;
var lcs_DOWN;
var lcs_EQUAL;
var lcs_DIAGONAL;

lcs_DELETION = lcs_RIGHT = -1;
lcs_INSERTION = lcs_DOWN = 1;
lcs_EQUAL = lcs_DIAGONAL = 0;

/**
 * Compare two strings and return the comparison.
 * @param reference: the refrence string.
 * @param input: the input string.
 */
function lcs_compare(reference, input) {
  var matrix = []

  // Fill the last line.
  for (var j = reference.length; 0 <= j; j--) {
    matrix[input.length] = matrix[input.length] || [];
    matrix[input.length][j] = { value: reference.length - j, status: lcs_DELETION, direction: lcs_RIGHT };
  }

  // Fill the last column.
  for (var i = input.length; 0 <= i; i--) {
    matrix[i] = matrix[i] || [];
    matrix[i][reference.length] = { value: input.length - i, status: lcs_INSERTION, direction: lcs_DOWN };
  }

  // Fill the last cell.
  matrix[input.length][reference.length] = { value: 0, status: lcs_EQUAL, direction: lcs_DIAGONAL };

  for (var j = reference.length - 1; 0 <= j; j--) {
    for (var i = input.length - 1; 0 <= i; i--) {
      lcs_backtrack(matrix, reference, j, input, i);
    }
  }

  return matrix;
};

/**
 * Run thought the path in the matrix and do the callback for each step.
 * @param matrix that contain the comparison.
 * @param i the start index for the comparison.
 * @param j the start index for the comparison.
 * @param callback to run on each step.
 */
lcs_run = function(matrix, i, j, callback) {
  // Return if we arrive at the end of the input string.
  if (i == matrix.length) {
    return;
  }

  // Do the action
  callback(i, j);

  // Continue to the next step.
  switch(matrix[i][j].direction) {
    case lcs_DIAGONAL: lcs_run(matrix, i + 1, j + 1, callback); break;
    case lcs_RIGHT: lcs_run(matrix, i, j + 1, callback); break;
    case lcs_DOWN: lcs_run(matrix, i + 1, j, callback); break;
  }
};

/**
 * Set the matrix value at the given index by reading values near it.
 * @param matrix to set value.
 * @param reference the reference string input.
 * @param j the index of the reference and the column in the matrix.
 * @param input the string reference input.
 * @param i the index of the input and the line in the matrix.
 */
lcs_backtrack = function(matrix, reference, j, input, i) {
  if (reference[j] == input[i]) {
    matrix[i][j] = { value: matrix[i + 1][j + 1].value, status: lcs_EQUAL, direction: lcs_DIAGONAL };
  } else if (matrix[i][j + 1].value < matrix[i + 1][j].value)
    matrix[i][j] = { value: matrix[i][j + 1].value + 1, status: lcs_DELETION, direction: lcs_RIGHT };
  else {
    matrix[i][j] = { value: matrix[i + 1][j].value + 1, status: lcs_INSERTION, direction: lcs_DOWN };
  }
};

// This function take the matrix "times" containing all the times for a given bib.
// times[0] = [time_site0_lap1, time_site0_lap2, time_site0_lap3, ...]
// times[1] = [time_site1_lap1, time_site1_lap2, time_site1_lap3, ...]
// times[2] = [time_site2_lap1, time_site2_lap2, time_site2_lap3, ...]
// It takes also the last pings for each site in order to know if a site may have a problem.
function check_times(times, pings, site_number) {
  var site_number = site_number || Math.min(times.length, pings.length);

  // Build the site vector. It is a vector of each step of the bib.
  var sites = [];
  for (var i = 0; i < site_number; i++) {
    for (var j = 0; times[i] && j < times[i].length; j++) {
      sites.push({
        time: times[i][j],
        site: i,
        lap: j
      });
    }
  }

  // Sort the site vector according to time and do a projection to the site index.
  sites.sort(function(s1, s2) { return s2.time < s1.time ? 1 : (s1.time < s2.time ? -1 : 0); });
  var input = '';
  var reference = '';
  var expected_site = 0;

  // Be sure that we start on the same expected site (they will be consider as missing).
  while (sites.length && sites[0].site != expected_site) {
    reference += expected_site;
    expected_site = (expected_site + 1) % site_number;
  }

  for (var i = 0; i < sites.length; i++) {
    // Build the input string.
    input += sites[i].site;

    // Be sure that the current time is ok with the last ping of the expected site.
    while (sites[i].time > pings[expected_site]) {
      expected_site = (expected_site + 1) % site_number;
    }

    // Build the reference string.
    reference += expected_site;
    expected_site = (expected_site + 1) % site_number;
  }

  // Be sure that we end on the same site.
  while (sites.length && sites[sites.length - 1].site != parseInt(reference[reference.length - 1])) {
    reference += expected_site;
    expected_site = (expected_site + 1) % site_number;
  }

//$.log('input: ' + input + ', reference: ' + reference);

  // Compare the site vector with the reference.
  var pb;
  var compare = lcs_compare(reference, input);
  lcs_run(compare, 0, 0, function(i, j) {
    // Already identified a pb, just return.
    if (pb) {
      return;
    }

    var status = compare[i][j].status;
    if (status) {
      pb = {
        site_id: parseInt(status == lcs_DELETION ? reference[j] : input[i]),
        type: status == lcs_DELETION ? "Manque un passage" : "Passage supplémentaire",
        lap: (status == lcs_DELETION ? parseInt(j / site_number) : sites[i].lap) + 1,
        next_site: status == lcs_DELETION ? sites[i].site : undefined,
        next_time: status == lcs_DELETION ? sites[i].time : undefined,
        prev_site: status == lcs_DELETION && i > 0 ? sites[i-1].site : undefined,
        prev_time: status == lcs_DELETION && i > 0 ? sites[i-1].time : undefined,
      }
    }
  });

  return pb;
}

/**
 * Do the check for the given matrix of times.
 * @param res: store the result of the action.
 * @param bib: the bib that we check.
 * @param times: matrix of times for each site.
 * @param deleted_times: the deleted times for this site.
 * @param pings: the pings for each site.
 */
function do_check_times(res, bib, times, deleted_times, artificial_times, pings) {
  // Check the times.
  // TODO: hardcoded number of sites
  var check = check_times(times, pings, 7);

  // There was a problem.
  if (check) {
    // Push the check object into the list of problems.
    res.pbs.push(check)

    // Set the bib for this check.
    check.bib = bib;

    // Display the times for each site.
    check.sites = [];

    // TODO: hardcoded number of sites
    for (var i = 0; i < 7; i++) {
      // Push the site object definition.
      check.sites.push({
        id: i,
        bib: bib,
        times: get_site_bib_times(check, times, i, bib),
        deleted_times: get_site_bib_deleted_times(deleted_times, i, bib),
        artificial_times: get_site_bib_artificial_times(artificial_times, i, bib)
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
 * Get the artificial times for the site and bib.
 * @param artificial_times: the list of all artificial times (for all sites).
 * @param i: the site id.
 * @param bib: the current bib.
 */
function get_site_bib_artificial_times(artificial_times, i, bib) {
  var site_bib_artificial_times = [];

  if (artificial_times[i]) {
    for (var j = 0; j < artificial_times[i].length; j++) {
      site_bib_artificial_times.push({
        val: format_date(new Date(artificial_times[i][j])),
        lap: j,
        bib: bib,
        site_id: i
      });
    }
  }

  return site_bib_artificial_times;
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


  start({
    "headers": {
      "Content-Type": "application/json"
    }
  });

  var times = [];
  var deleted_times = [];
  var artificial_times = [];
  var pings = JSON.parse(req.query.pings);
  var res = { pings: pings, pbs: [] };

  var row = getRow();
  var current_bib = -1;

  if (row) {
    current_bib = row.key[0];
  }
  while (row) {
    var bib = row.key[0];

    // we can now check for previous bib
    if (bib != current_bib) {
      // Do the check.
      do_check_times(res, current_bib, times, deleted_times, artificial_times, pings);

      // Empty the times table for next bib check.
      times = [];
      deleted_times = [];
      artificial_times = [];

      // Update current bib for the next check.
      current_bib = bib;
    }

    // Just keep the info for all rows
    times[row.value.site_id] = row.value.times;
    deleted_times[row.value.site_id] = row.value.deleted_times;
    artificial_times[row.value.site_id] = row.value.artificial_times;
    row = getRow();
  }

  if (current_bib != -1) {
    // Do the check for the last bib.
    do_check_times(res, current_bib, times, deleted_times, artificial_times, pings);
  }

  // Return the data.
  return JSON.stringify(res);
}
