function open_or_null(app, doc_id, cb) {
  app.db.openDoc(doc_id, {
    success: cb,
    error: function() { cb(); }
  });
}
function open_or_fail(app, doc_id, cb, fail_msg) {
  app.db.openDoc(doc_id, {
    success: cb,
    error: function(status, req, e) {
      ok(false, fail_msg + "," + status
                + "," +req+ "," + e);
      start();
    }});
}

function bib_assert(app, bib, expected_race_id, expected_length, cb) {
  open_or_fail(app, checkpoints_id(bib, app.site_id), function(checkpoints) {
    equal(checkpoints.site_id, app.site_id, "wrong site_id inserted");
    equal(checkpoints.times.length, expected_length, "wrong checkpoints times length");
    equal(checkpoints.race_id, expected_race_id, "wrong race_id inserted");
    var sorted_times = checkpoints.times.slice().sort(function(a,b) {return a-b});
    deepEqual(sorted_times, checkpoints.times,
       "Timestamps should be sorted");
    cb(checkpoints);
  }, "Error getting freshly inserted checkpoint");
}

function add_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  open_or_null(app, checkpoints_id(bib, app.site_id), function (doc) {
    var previous_checkpoints = (doc && doc.times) || [];
    add_checkpoint(bib, app, ts, function() {
      bib_assert(app, bib, expected_race_id, expected_length, function(checkpoints) {
        var margin_error = ts && 0 || 10000 ;
        ts = ts || new Date().getTime();
        var inserted_ts = _.difference(checkpoints.times, previous_checkpoints)[0];
        var last_ts_diff = Math.abs(ts - inserted_ts);
        ok(last_ts_diff <= margin_error, "Wrong timestamp inserted: diff is " + last_ts_diff, "inserted should have been approximately" + ts);
        cb(checkpoints);
      });
    });
  });
}

function remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  remove_checkpoint(bib, app, ts, function() {
    bib_assert(app, bib, expected_race_id, expected_length, cb)
  });
}

ASSERTS_PER_SINGLE_INSERT_DELETE = 9;
function test_single_bib_insertion(app, bib, expected_race_id, ts) {
  add_checkpoint_and_assert(app, bib, ts, expected_race_id, 1, function(checkpoints) {
    var ts=checkpoints.times[0];
    remove_checkpoint_and_assert(app, bib, ts, expected_race_id, 0, function(checkpoints) {
      start();
    });
  });
  return ASSERTS_PER_SINGLE_INSERT_DELETE;
}

function async_for(N, i, loop, cb) {
  if (i < N) {
   loop(i, function() {
    async_for(N, i+1, loop, cb)
   });
  }
  else {
    cb();
  }
}
function async_foreach(seq, loop, cb) {
  async_for(seq.length, 0, function(i, cb) {
    loop(seq[i], i, seq, cb);
  }, cb);
}

function add_checkpoints_and_assert(tss, app, bib, expected_race_id, cb) {
  async_foreach(tss, function(ts, i, tss, cb) {
    add_checkpoint_and_assert(app, bib, ts, expected_race_id, i+1, cb);
  }, cb);
}

function remove_checkpoints_and_assert(N, app, bib, expected_race_id, cb) {
  async_for(N, 0, function(i, cb) {
    open_or_fail(app, checkpoints_id(bib, app.site_id), function(checkpoints) {
      var expected_length = N - i;
      var ts = checkpoints.times[expected_length-1];
      remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length-1, cb);
    });
  }, cb);
}
function test_multiple_bib_insertion(app, bib, expected_race_id, tss) {
  var N = tss.length;
  add_checkpoints_and_assert(tss, app, bib, expected_race_id, function() {
    remove_checkpoints_and_assert(N, app, bib, expected_race_id, function() {
      start();
    });
  });
  return ASSERTS_PER_SINGLE_INSERT_DELETE*N;
}

function foreach_unwrapped_checkpoints(app, checkpoints, f, cb) {
  async_foreach(checkpoints, function(checkpoint, i, checkpoints, cb) {
    f(checkpoint.bib, app, checkpoint.ts, cb, checkpoint.site_id);
  }, cb);
}
function insert_checkpoints(app, checkpoints, cb) {
  foreach_unwrapped_checkpoints(app, checkpoints, add_checkpoint, cb);
}
function delete_checkpoints(app, checkpoints, cb) {
  foreach_unwrapped_checkpoints(app, checkpoints, remove_checkpoint, cb);
}

function with_temp_checkpoints(app, checkpoints, f, cb) {
  insert_checkpoints(app, checkpoints, function() {
    f(function() {
      delete_checkpoints(app, checkpoints, cb);
    });
  });
}

function with_temp_checkpoints_and_start(app, checkpoints, cb) {
  with_temp_checkpoints(app, checkpoints, cb, function() {
    start();
  });
}
function timestamp_at_lap(checkpoints, bib, lap) {
  //$.log("TIMESTAMP : lap " + lap + " bib " + bib + "(checkpoints = " + JSON.stringify(checkpoints) + ")");
  var result = _.filter(checkpoints, function(checkpoint) { return checkpoint.bib == bib })[lap-1].ts;
  //$.log("result " + result);
  return result;
}
function test_previous(app, bib, lap, checkpoints, expected) {
  expect(1);
  with_temp_checkpoints_and_start(app, checkpoints, function(cb) {
    var ts = timestamp_at_lap(checkpoints, bib, lap);
    var data = {bib: bib, lap: lap, ts: ts, race: 1};
    db_previous(app, app.site_id, data, function(data) {
      var bibs = {
        predecessors: data.predecessors.map(function(predecessor) { return predecessor.value.bib }),
        rank: data.rank
      };
      deepEqual(bibs, expected, "Compare the data given to the callback given to db_previous");
      cb();
    });
  });
}
function test_average(app, bib, lap, checkpoints, expected) {
  expect(1);
  with_temp_checkpoints_and_start(app, checkpoints, function(cb) {
    var ts = timestamp_at_lap(checkpoints, bib, lap);
    var data = {bib: bib, lap: lap, ts: ts, race: 1};
    db_previous(app, app.site_id, data, function(data) {
      var average = data.average;
      deepEqual(average, expected, "Compare the data given to the callback given to db_previous");
      cb();
    });
  });
}
function test_global(app, checkpoints, expected, race_id) {
  expect(expected.length);
  with_temp_checkpoints_and_start(app, checkpoints, function(cb) {
    db_global_ranking(app, function(data) {
      console.log(JSON.stringify(data));
      console.log(data);
      var bibs = data.data.rows[0].contestants.map(function(contestant) { return contestant.value.bib; });
      deepEqual(bibs, expected.bibs, "Check bibs order for race " + expected.race_id);
      cb();
    }, race_id);
  });
}
function test_bib_input(app) {
  db_app_data(app, function() {
    module("setup");
    test("setup ok", function() {
        expect(1);
        ok(app.site_id != undefined, "undefined site id");
    });
    module("bib insertion");
    asyncTest("checkpoint insertion (with infos)", function() {
      expect(test_single_bib_insertion(app, 0, 1));
    });
    asyncTest("checkpoint insertion (without infos)", function() {
      expect(test_single_bib_insertion(app, 999, 0));
    });
    asyncTest("checkpoint insertion (with infos), forced timestamp", function() {
      expect(test_single_bib_insertion(app, 0, 1, 23498123));
    });
    asyncTest("multiple checkpoints insertion (with infos)", function() {
      expect(test_multiple_bib_insertion(app, 500, 0, [0, 234125, 0]));
    });
    module("previous");
    asyncTest("1 lap", function() {
      test_previous(app, 2, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 2, ts: 1200, site_id:0 }
      ], {
        predecessors:[ 2, 1, 0],
        rank: 3
      });
    });
    asyncTest("1 lap, cornercase first bib", function() {
      test_previous(app, 0, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 2, ts: 1200, site_id:0 }
      ], {
        predecessors:[0],
        rank: 1
      });
    });
    asyncTest("1 lap, 2 sites", function() {
      test_previous(app, 2, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:1 },
        { bib: 2, ts: 1200, site_id:0 }
      ], {
        predecessors:[2, 0],
        rank: 2
      });
    });
    var tmp = [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 0, ts: 2100, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 1, ts: 2000, site_id:0 },
        { bib: 2, ts: 1200, site_id:0 },
        { bib: 2, ts: 2200, site_id:0 },
    ] ;
    asyncTest("2 laps, lap1", function() {
      test_previous(app, 2, 1, tmp, {
        predecessors:[2, 1, 0],
        rank: 3
      });
    });
    asyncTest("2 laps, lap2", function() {
      test_previous(app, 2, 2, tmp, {
        predecessors:[2, 0, 1],
        rank: 3
      });
    });
    asyncTest("more than 4 bibs", function() {
      test_previous(app, 12, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 2, ts: 1200, site_id:0 },
        { bib: 10, ts: 1300, site_id:0 },
        { bib: 11, ts: 1400, site_id:0 },
        { bib: 12, ts: 1500, site_id:0 },
      ], {
        predecessors:[12, 11, 10],
        rank: 6
      });
    });
    module("average");
    asyncTest("average same site", function() {
      test_average(app, 10, 2, [
        { bib: 10, ts: 1000, site_id:0 },
        { bib: 10, ts: 2000, site_id:0 }
      ], {
        avg_present: true,
        last_site:0,
        last_timestamp:1000,
        last_lap:1
      });
    });


    module("global");
    asyncTest("very simple", function() {
      test_global(app, [
        { bib: 2, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
      ], { race_id:1, bibs:[2, 1]} , 1);
    });
    asyncTest("very simple with bib zero", function() {
      test_global(app, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
      ], { race_id:1, bibs:[0, 1]}, 1);
    });
    asyncTest("2 races, different laps", function() {
      test_global(app, [
        { bib: 2, ts: 800, site_id:0 },
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 0, ts: 2000, site_id:1 },
        { bib: 1, ts: 2100, site_id:1 },
        { bib: 0, ts: 3000, site_id:2 },
        { bib: 3, ts: 12100, site_id:0 },
        { bib: 4, ts: 13000, site_id:0 },
        { bib: 4, ts: 13000, site_id:1 },
      ], { race_id:1, bibs:[0, 1, 2]}, 1);
    });
    asyncTest("2 races, different laps", function() {
      test_global(app, [
        { bib: 2, ts: 800, site_id:0 },
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 0, ts: 2000, site_id:1 },
        { bib: 1, ts: 2100, site_id:1 },
        { bib: 0, ts: 3000, site_id:2 },
        { bib: 3, ts: 12100, site_id:0 },
        { bib: 4, ts: 13000, site_id:0 },
        { bib: 4, ts: 13000, site_id:1 },
      ], { race_id:2, bibs:[4, 3]}, 2);
    });
    asyncTest("missing checkpoints", function() {
      test_global(app, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 0, ts: 3000, site_id:2 },
        { bib: 1, ts: 4100, site_id:0 },
        { bib: 0, ts: 5000, site_id:0 },
      ], { race_id:1, bibs:[1, 0]}, 1);
    });
  });
};

function test_admin(app) {
  module("check_time");
  test("missing first checkpoint", function() {
    expect(1);

    var times = [];
    times[0] = [];
    times[1] = [10];
    times[2] = [];
    result = check_times(times, [50, 50, 50]);

    expected = {
      site_id : 0,
      type : "Manque un passage",
      lap : 1,
      "next_site": 1,
      "next_time": 10,
      "prev_site": undefined,
      "prev_time": undefined
    };

    deepEqual(result, expected, "Should return missing first statement.");
  });


  test("Ok one time", function() {
    expect(1);

    var times = [];
    times[0] = [10];
    times[1] = [];
    times[2] = [];
    result = check_times(times, [50, 50, 50]);

    expected = undefined;

    deepEqual(result, expected, "Should return nothing.");
  });

  test("Ok 5 times", function() {
    expect(1);

    var times = [];
    times[0] = [10, 13];
    times[1] = [11, 14];
    times[2] = [12];
    result = check_times(times, [50, 50, 50]);

    expected = undefined;

    deepEqual(result, expected, "Should return nothing.");
  });

  test("Ok all times", function() {
    expect(1);
    var times = [];

    times[0] = [10, 13, 16, 19, 22];
    times[1] = [11, 14, 17, 20, 23];
    times[2] = [12, 15, 18, 21, 24];
    result = check_times(times, [50, 50, 50]);

    expected = undefined;

    deepEqual(result, expected, "Should return nothing.");
  });

  test("Ok all times but 1 site", function() {
    expect(1);

    var times = [];
    times[0] = [10, 13, 16, 19, 22];
    times[1] = [11];
    times[2] = [12, 15, 18, 21, 24];
    result = check_times(times, [50, 12, 50]);

    expected = undefined;

    deepEqual(result, expected, "Should return nothing.");
  });

  test("Ok all times but 2 sites", function() {
    expect(1);

    var times = [];
    times[0] = [10, 13];
    times[1] = [11, 14, 17, 20, 23];
    times[2] = [12, 15, 18];
    result = check_times(times, [15, 50, 20]);

    expected = undefined;

    deepEqual(result, expected, "Should return nothing.");
  });

}
