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
  return {name:"", first_name:"", race:""};
}

function pad2(number) {
  return ((number < 10) && (number >= 0) ? '0' : '') + number
}

function time_to_hour_string(t) {
  date = new Date(t);
  return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
}

function hms_to_string(h, m, s) {
  h = pad2(h);
  m = pad2(m);
  s = pad2(s);
  return h+":"+m+":"+s;
}
//pluralize heure, minute and seconde in french
function pluralize(n, str, display_zero) {
  if (n==0 && !display_zero)
    return "";
  var prefix = n+" "+str;
  if (n==1) return prefix;
  return prefix+"s";
}
function language_and(values, strings) {
  var result="";
  var display_zero=false;
  var linking_str;
  var l = strings.length;
  for (var i=0; i<l; i=i+1) {
    if ((values[i] != 0) || (i==(l-1))) {
      display_zero=true;
    }
    if (i==(l-1) || !display_zero) {
      linking_str = "";
    } else if (i==(l-2)) {
      linking_str = " et ";
    } else {
      linking_str = ", ";
    }
    result += pluralize(values[i], strings[i], display_zero) + linking_str;
  }
  return result;
}
function hms_to_human_string(h, m, s) {
  return language_and([h,m,s], ["heure", "minute", "seconde"]);
}
function date_to_string(date) {
  return hms_to_string(date.getHours(), date.getMinutes(), date.getSeconds());
}
function int_to_hms(ts) {
  sec = Math.floor(ts / 1000);
  min = Math.floor(sec / 60);
  // we don't take % 24 in order to be coherent with global average
  // The race day we MUST have the correct start-time for each race.
  hour = Math.floor(min / 60);
  return { sec:(sec%60), min:(min%60), hour:hour };
}
function int_to_datestring(ts) {
  var hms = int_to_hms(ts);
  return hms_to_string(hms.hour, hms.min, hms.sec);
}
function int_to_human_string(ts) {
  var hms = int_to_hms(ts);
  return hms_to_human_string(hms.hour, hms.min, hms.sec);
}
function update_clock_on_div(div) {
  $(div).html(date_to_string(new Date()));
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
  var key = ev.which?ev.which:window.event.keyCode;
  var is_number = ((key >= 48) && (key <=57));
  var is_keypad_number = ((key >= 96) && (key <=105));
  if ( is_number || is_keypad_number ) {
    // If bib_input already has focus, return
    if ($("#bib_input").find("input")[0] == document.activeElement)
      return false;

    $("#bib_input").find("input")[0].focus();
    // TODO find a better way to put the key here
    var key_value = is_number ? (key-48) : (key-96);
    $("#bib_input").find("input")[0].value += key_value;
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

function increment_string_key(str) {
  return str+'\ufff0';
}

//Copy paste from search/lists/intersect-search.js
//TODO find a solution
function search_nonmatch_field(str) {
  if (str == "name")
    return "first_name"
  else
    return "name";
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


function cat_from_year(year) {
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
    return 8; // master 1
  else if (age <= 59)
    return 9; // master 2
  else if (age <= 69)
    return 10; // master 3
  else if (age <= 79)
    return 11; // master 4
  else
    return 12; // master 5
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
        : 0);
    },
    error: function() { callback(); }
  });
};

function capitaliseFirstLetter(string) {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

function highlight_search(row) {
  var split = row.split;
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
  if (row.value.match == "name") {
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

function isTimestampValid(timestamp) {
  return ((typeof timestamp) == "number") &&
    ($.isNumeric(timestamp)) &&
    (timestamp > 0) &&
    (timestamp % 1) === 0;
}
