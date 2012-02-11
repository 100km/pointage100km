function setup_site_info(app, cb) {
  app.db.saveDoc({
    _id: "_local/site_info",
    site_id: 0
  }, {
    success: function() {
      app.site_id=0;
      cb();
    },
    error: function() {
      app.site_id=0;
      cb();
    }
  });
}
function setup_bib_info(app, cb) {
  app.db.saveDoc({
    _id: infos_id(0),
    dossard: 0,
    course: 1,
  }, {
    success: cb,
    error: cb  });
}
function setup_app(app, cb) {
  fork([
      function(cb) { setup_site_info(app, cb) },
      function(cb) { setup_bib_info(app, cb) }
  ], cb);
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
    cb(checkpoints);
  }, "Error getting freshly inserted checkpoint");
}

function submit_bib_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  submit_bib(bib, app, ts, function() {
    bib_assert(app, bib, expected_race_id, expected_length, function(checkpoints) {
      var margin_error = ts && 0 || 10000 ;
      ts = ts || new Date().getTime()
      var last_ts_diff = Math.abs(ts - checkpoints.times[expected_length-1]);
      ok(last_ts_diff <= margin_error, "Wrong timestamp inserted: diff is " + last_ts_diff, "inserted should have been approximately" + ts);
      cb(checkpoints);
    });
  });
}

function submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length, cb) {
  submit_remove_checkpoint(bib, app, ts, function() {
    bib_assert(app, bib, expected_race_id, expected_length, cb)
  });
}

ASSERTS_PER_SINGLE_INSERT_DELETE = 7;
function test_single_bib_insertion(app, bib, expected_race_id, ts) {
  submit_bib_and_assert(app, bib, ts, expected_race_id, 1, function(checkpoints) {
    var ts=checkpoints.times[0];
    submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, 0, function(checkpoints) {
      start();
    });
  });
  return ASSERTS_PER_SINGLE_INSERT_DELETE;
}

function repeat(N, i, loop, cb) {
  if (i < N) {
   loop(i, function() {
    repeat(N, i+1, loop, cb)
   });
  }
  else {
    cb();
  }
}

function submit_bibs_and_assert(N, tss, app, bib, expected_race_id, cb) {
  repeat(N, 0, function(i, cb) {
    submit_bib_and_assert(app, bib, tss[i], expected_race_id, i+1, cb);
  }, cb);
}

function submit_remove_checkpoints_and_assert(N, app, bib, expected_race_id, cb) {
  repeat(N, 0, function(i, cb) {
    open_or_fail(app, checkpoints_id(bib, app.site_id), function(checkpoints) {
      var expected_length = N - i;
      var ts = checkpoints.times[expected_length-1];
      submit_remove_checkpoint_and_assert(app, bib, ts, expected_race_id, expected_length-1, cb);
    });
  }, cb);
}
function test_multiple_bib_insertion(app, bib, expected_race_id, tss) {
  var N = tss.length;
  submit_bibs_and_assert(N, tss, app, bib, expected_race_id, function() {
    submit_remove_checkpoints_and_assert(N, app, bib, expected_race_id, function() {
      start();
    });
  });
  return ASSERTS_PER_SINGLE_INSERT_DELETE*N;
}

function test_bib_input(app) {
  setup_app(app, function() {
    module("bib_input"); 
    test("setup ok", function() {
        expect(1);
        ok(app.site_id != undefined, "undefined site id");
    });
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
  });
};
