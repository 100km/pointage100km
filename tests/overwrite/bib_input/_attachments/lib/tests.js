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

function integer_array_equal(arr1, arr2) {
  return _.all(_.zip(arr1, arr2), function(a) { return a[0] == a[1];});
}
function bib_assert(app, bib, expected_race_id, expected_length, cb) {
  open_or_fail(app, checkpoints_id(bib, app.site_id), function(checkpoints) {
    equal(checkpoints.site_id, app.site_id, "wrong site_id inserted");
    equal(checkpoints.times.length, expected_length, "wrong checkpoints times length");
    equal(checkpoints.race_id, expected_race_id, "wrong race_id inserted");
    var sorted_times = checkpoints.times.slice().sort(function(a,b) {return a-b});
    ok(integer_array_equal(sorted_times, checkpoints.times),
       "Timestamps are not sorted");
    cb(checkpoints);
  }, "Error getting freshly inserted checkpoint");
}

function submit_bib_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  open_or_null(app, checkpoints_id(bib, app.site_id), function (doc) {
    var previous_checkpoints = (doc && doc.times) || [];
    submit_bib(bib, app, ts, function() {
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

function submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  submit_remove_checkpoint(bib, app, ts, function() {
    bib_assert(app, bib, expected_race_id, expected_length, cb)
  });
}

ASSERTS_PER_SINGLE_INSERT_DELETE = 9;
function test_single_bib_insertion(app, bib, expected_race_id, ts) {
  submit_bib_and_assert(app, bib, ts, expected_race_id, 1, function(checkpoints) {
    var ts=checkpoints.times[0];
    submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, 0, function(checkpoints) {
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

function submit_bibs_and_assert(tss, app, bib, expected_race_id, cb) {
  async_foreach(tss, function(ts, i, tss, cb) {
    submit_bib_and_assert(app, bib, ts, expected_race_id, i+1, cb);
  }, cb);
}

function submit_remove_checkpoints_and_assert(N, app, bib, expected_race_id, cb) {
  async_for(N, 0, function(i, cb) {
    open_or_fail(app, checkpoints_id(bib, app.site_id), function(checkpoints) {
      var expected_length = N - i;
      var ts = checkpoints.times[expected_length-1];
      submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length-1, cb);
    });
  }, cb);
}
function test_multiple_bib_insertion(app, bib, expected_race_id, tss) {
  var N = tss.length;
  submit_bibs_and_assert(tss, app, bib, expected_race_id, function() {
    submit_remove_checkpoints_and_assert(N, app, bib, expected_race_id, function() {
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
  foreach_unwrapped_checkpoints(app, checkpoints, submit_bib, cb);
}
function delete_checkpoints(app, checkpoints, cb) {
  foreach_unwrapped_checkpoints(app, checkpoints, submit_remove_checkpoint, cb);
}

function with_temp_checkpoints(app, checkpoints, f, cb) {
  insert_checkpoints(app, checkpoints, function() {
    f(function() {
      delete_checkpoints(app, checkpoints, cb);
    });
  });
}
function test_previous(app, bib, lap, checkpoints, expected) {
  expect(1);
  with_temp_checkpoints(app, checkpoints, function(cb) {
    var kms = site_lap_to_kms(app, app.site_id, lap);
    call_with_previous(app, app.site_id, bib, lap, kms, function(data) {
      var bibs = data.bibs.map(function(bib) { return bib.bib });
      ok(integer_array_equal(bibs, expected), "local ranking is false");
      cb();
    });
  }, function() {
    start();
  });
}
function test_bib_input(app) {
  call_with_app_data(app, function() {
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
      ], [ 0, 1]);
    });
    asyncTest("1 lap, cornercase first bib", function() {
      test_previous(app, 0, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:0 },
        { bib: 2, ts: 1200, site_id:0 }
      ], []);
    });
    asyncTest("1 lap, 2 sites", function() {
      test_previous(app, 2, 1, [
        { bib: 0, ts: 1000, site_id:0 },
        { bib: 1, ts: 1100, site_id:1 },
        { bib: 2, ts: 1200, site_id:0 }
      ], [0]);
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
      test_previous(app, 2, 1, tmp, [0, 1]);
    });
    asyncTest("2 laps, lap2", function() {
      test_previous(app, 2, 2, tmp, [1, 0]);
    });
  });
};
