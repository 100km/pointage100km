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
  return (app.kms_offset[site_id] + (lap - 1) * app.kms_lap).toFixed(2);
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

//Copy paste from search/lists/intersect-search.js
//TODO find a solution
function search_nonmatch_field(str) {
  if (str == "nom")
    return "prenom"
  else
    return "nom";
}

function remove_accents(str) {
  var changes = [
    { base: "a", letters: /[àáâãäå]/g },
    { base: "e", letters: /["éèêë"]/g },
    { base: "i", letters: /[ìíîï]/g },
    { base: "o", letters: /["òóôõö"]/g },
    { base: "u", letters: /[ùúûü]/g },
    { base: "y", letters: /[ýÿ]/g },
    { base: "oe", letters: /œ/g },
    { base: "ae", letters: /æ/g },
    { base: "c", letters: /ç/g },
    { base: "n", letters: /ñ/g }
  ];
  for(var i=0; i<changes.length; i++) {
    str = str.replace(changes[i].letters, changes[i].base);
  }
  return str;
}


function cat_from_year(year, is_woman) {
  var date = new Date();
  var age = date.getYear() - year;

  if (age <= 9)
    return 0; // eveil
  else if (age <= 11)
    return 1; // poussin
  else if (age <= 13)
    return 2; // benjamin
  else if (age <= 15)
    return 3; // minime
  else if (age <= 17)
    return 4; // cadet
  else if (age <= 19)
    return 5; // junior
  else if (age <= 22)
    return 6; // espoir
  else if (age <= 39)
    return 7; // senior
  else if (age <= 49)
    return 8; // veteran 1
  else if (age <= 59)
    return 9; // veteran 2
  else if (age <= 69)
    return 10; // veteran 3
  else if (is_woman)
    return 10; // only veteran 3 for women
  else
    return 11; // veteran 4
}


function format_date(date) {
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}


function get_ping(app, ping, callback) {
  app.db.view('admin/alive', {
    key: ping,
    reduce: true,
    success: function(view) {
      callback(view.rows.length > 0
        ? view.rows[0].value.max
        : new Date(0));
    },
    error: function() { callback(); }
  });
};

function capitaliseFirstLetter(string) {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

function highlight_search(split, row) {
  var firstvalue = row.value[row.value.match];
  var secondvalue= row.value[search_nonmatch_field(row.value.match)];
  var value;
  var label1 = "<strong>" + firstvalue.substr(0, split[0].length) +
     "</strong>" + firstvalue.substr(split[0].length);
  var label2;
  if (split.length>1) {
    label2 = "<strong>" + secondvalue.substr(0, split[1].length) + "</strong>" +
      secondvalue.substr(split[1].length);
  } else {
    label2 = secondvalue;
  }
  var label;
  if (row.value.match == "nom") {
    label = "<span class=family-name>" + label1 + "</span> <span class=firstname>" + label2 + "</span"
    value = firstvalue.toUpperCase() + " " + capitaliseFirstLetter(secondvalue);
  } else {
    label = "<span class=firstname>" + label1 + "</span> <span class=family-name>" + label2 + "</span"
    value = capitaliseFirstLetter(firstvalue) + " " + secondvalue.toUpperCase();
  }
  return {
    label: label,
    value: value
  };
}
