function unwrap_data(data) {
  return data.rows.map(function(row) {
    return row.value;
  });
}

function retries(n, f, debug_name) {
  if (n<=0) {
    alert("Too many retries for " + debug_name);
  } else {
    f(function () {
      retries(n-1, f, debug_name);
    });
  }
}

function handle_not_found_or_die(f) {
  return function(stat, err, reason) {
    if(not_found(stat, err, reason)) {
      f();
    }
    else {
      $.log("stat" + JSON.stringify(stat));
      $.log("err" + JSON.stringify(err));
      $.log("reason" + JSON.stringify(reason));
      alert("Error, but not missing doc");
    }
  }
}

function not_found(stat, err, reason) {
  return (stat==404 && err == "not_found" && (reason == "missing" || reason=="deleted"));
}

function isBib(bib) {
  var isBib_re       = /^\d+$/;
  return String(bib).search (isBib_re) != -1
}

// From http://blog.yjl.im/2010/01/stick-div-at-top-after-scrolling.html
function place_previous() {
  var window_top = $(window).scrollTop();
  var div_top = $('#previous_container').offset().top;
  if (window_top > div_top)
    $('#previous').addClass('stick')
  else
    $('#previous').removeClass('stick');
}

function empty_info() {
  return {nom:"", prenom:"", course:""};
}

function pad2(number) {
  return ((number < 10) && (number >= 0) ? '0' : '') + number
}

function time_to_hour_string(t) {
  date = new Date(t);
  return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
}

function update_clock_on_div(div) {
  var current_time = new Date();
  var h = pad2(current_time.getHours());
  var m = pad2(current_time.getMinutes());
  var s = pad2(current_time.getSeconds());
  $(div).html(h+":"+m+":"+s);
}

//From http://stackoverflow.com/questions/4631774/coordinating-parallel-execution-in-node-js
function fork (async_calls, shared_callback) {
  var counter = async_calls.length;
  var all_results = [];
  function makeCallback (index) {
    return function () {
      counter --;
      var results = [];
      // we use the arguments object here because some callbacks
      // in Node pass in multiple arguments as result.
      for (var i=0;i<arguments.length;i++) {
        results.push(arguments[i]);
      }
      all_results[index] = results;
      if (counter == 0) {
        shared_callback(all_results);
      }
    }
  }

  for (var i=0;i<async_calls.length;i++) {
    async_calls[i](makeCallback(i));
  }
}

function get_doc(app, cb, doc_name) {
  app.db.openDoc(doc_name, {
    success: function(data) {
      cb(data);
    },
    error: function(status) {
      console.log(status);
    }
  });
}

function change_li(li, app) {
  // Select the new li element by trigering the 'select_item' event with the corresponding data.
  var bib = li.find('input[name="bib"]').val();
  var lap = li.find('input[name="lap"]').val();
  li.trigger('select_item', li.data('checkpoint'));
}

function deal_with_key(ev, app) {
  key = ev.which?ev.which:window.event.keyCode;

  if ((key >= 48) && (key <=57)) { //figures
    // If bib_input already has focus, return
    if ($("#bib_input").find("input")[0] == document.activeElement)
      return false;

    $("#bib_input").find("input")[0].focus();
    // TODO find a better way to put the key here
    $("#bib_input").find("input")[0].value += (key-48);
  } else if (key == 40) { // down arrow
    change_li($('#items li.selected').next(), app)
  } else if (key == 38) { // up arrow
    change_li($('#items li.selected').prev(), app)
  }

  // return false is equivalent to ev.stopPropagation
  return false;
}

function checkBib(bib) {
  if (bib == "") {
    $("#bib_check").html("");
  } else if (isBib(bib)) {
    $("#bib_check").html("<img src=img/check.png></img>");
  } else {
    $("#bib_check").html("<p>Dossard invalide</p>");
  }
}

function site_lap_to_kms(app, site_id, lap) {
  return app.kms_offset[site_id] + (lap - 1) * app.kms_lap;
}


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
  var compare = lcs.compare(reference, input);
  lcs.run(compare, 0, 0, function(i, j) {
    // Already identified a pb, just return.
    if (pb) {
      return;
    }

    var status = compare[i][j].status;
    if (status) {
      pb = {
        site_id: parseInt(status == lcs.DELETION ? reference[j] : input[i]),
        type: status == lcs.DELETION ? "Manque un passage" : "Passage supplémentaire",
        lap: (status == lcs.DELETION ? parseInt(j / site_number) : sites[i].lap) + 1,
        next_site: status == lcs.DELETION ? sites[i].site : undefined,
        next_time: status == lcs.DELETION ? sites[i].time : undefined,
        prev_site: status == lcs.DELETION && i > 0 ? sites[i-1].site : undefined,
        prev_time: status == lcs.DELETION && i > 0 ? sites[i-1].time : undefined,
      }
    }
  });

  return pb;
}

function increment_string_key(str) {
  return str+'\ufff0';
}
