function submit_bib(bib, app, cb) {
  retries(3, function(fail) {
    submit_bib_once(bib, app, cb, fail);
  }, "submit_bib");
}
function submit_bib_once(bib, app, cb, fail) {
  call_with_race_id(bib, app, function(race_id) {
    call_with_checkpoints(bib, app, function(checkpoints) {
      if (checkpoints["bib"] == undefined) {
        checkpoints = new_checkpoints(bib, race_id, app.site_id);
      }
      add_checkpoint(checkpoints);
      app.db.saveDoc(checkpoints, {
        success: cb,
        error: fail
      });
    });
  });
}

function retries(n, f, debug_name) {
  if (n<=0) {
    alert("Too many retries for " + debug_name);
  } else {
    f(function () {
      retries(n-1, f);
    });
  }
}

function call_with_race_id(bib, app, f) {
  var invalid_race_id = 0;
  app.db.openDoc(infos_id(bib), {
    success: function(bib_info) {
      //0 is invalid as a race_id
      //valid race_ids are 1,2,4,8
      var race_id = bib_info["course"] || invalid_race_id;
      f(race_id);
    },
    error: function(stat, err, reason) {
     if(not_found(stat, err, reason)) {
      f(invalid_race_id);
     }
     else {
       $.log("stat" + JSON.stringify(stat));
       $.log("err" + JSON.stringify(err));
       $.log("reason" + JSON.stringify(reason));
       alert("Error, but not missing doc");
     }
    }
  });
}

function not_found(stat, err, reason) {
  return (stat==404 && err == "not_found" && (reason == "missing" || reason=="deleted"));
}

function call_with_checkpoints(bib, app, f) {
  app.db.openDoc(checkpoints_id(bib, app.site_id), {
    success: function(checkpoints) {
      $.log(" got " + JSON.stringify(checkpoints));
      f(checkpoints);
    },
    error: function(stat, err, reason) {
     if(not_found(stat, err, reason)) {
      f({});
     }
     else {
       $.log("stat" + JSON.stringify(stat));
       $.log("err" + JSON.stringify(err));
       $.log("reason" + JSON.stringify(reason));
       alert("Error, but not missing doc");
     }
    }
  });
}

function checkpoints_id(bib, site_id) {
  return "checkpoints-" + site_id + "-" + bib;
}

function infos_id(bib) {
  return "contestant-" + bib;
}

function new_checkpoints(bib, race_id, site_id) {
  var checkpoints = {};
  checkpoints._id = checkpoints_id(bib, site_id);
  checkpoints.bib = bib;
  checkpoints.race_id = race_id;
  checkpoints.site_id = site_id;
  checkpoints.times = [];
  checkpoints.deleted_times = [];
  return checkpoints;
}

function add_checkpoint(checkpoints) {
  var ts = new Date().getTime();
  checkpoints["times"].push(ts);
}

function submit_remove_checkpoint(bib, app, ts) {
  retries(3, function(fail) {
    submit_remove_checkpoint_once(bib, app, ts, fail);
  }, "remove checkpoint");
}
function submit_remove_checkpoint_once(bib, app, ts, fail) {
  call_with_checkpoints(bib, app, function(checkpoints) {
    remove_checkpoint(checkpoints, ts);
    app.db.saveDoc(checkpoints, {
      error: fail
    });
  });
}
function remove_checkpoint(checkpoints, ts) {
  $.log("removing " + ts + " in " + checkpoints["times"]);
  //Why doesn't indexOf work ?!?
  for (var i = checkpoints["times"].length-1; i>=0; i--) {
    if (ts == checkpoints["times"][i]) {
      checkpoints["deleted_times"].push(checkpoints["times"].splice(i, 1)[0]);
      return;
    }
  }
}

function isBib(bib)
{
  var isBib_re       = /^\d+$/;
  return String(bib).search (isBib_re) != -1
}

function place_arrow(obj) {
  if (obj == undefined)
    return;

  var $_arrow=$("#arrow");
  var pos = obj.offset();

  if (pos == null)
    return;

  var x_offset = obj.width();
  var y_offset = (obj.height()-42)/2+4; // +4 because of the border
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;

  // Set the div to link concurrent with its infos
  $_arrow.css({ marginLeft: 0, marginTop: 0,
                top: pos.top, left: pos.left });
  $_arrow.show();
}

function empty_info() {
  return {nom:"", prenom:"", course:""};
}

function pad2(number) {
  return (number < 10 ? '0' : '') + number
}

function time_to_hour_string(t) {
  date = new Date(t);
  return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
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

// TODO : remove console.log
function get_doc(app, cb, doc_name) {
  $.log("in get_doc " + doc_name);
  app.db.openDoc(doc_name, {
    success: function(data) {
      console.log(data);
      cb(data);
    },
    error: function(status) {
      console.log(status);
    }
  });
}

