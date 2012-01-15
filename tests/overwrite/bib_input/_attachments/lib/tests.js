//From http://stackoverflow.com/questions/4631774/coordinating-parallel-execution-in-node-js
function fork (async_calls, shared_callback) {
  var counter = async_calls.length;
  var callback = function () {
    $.log("counter : " + counter);
    counter --;
    if (counter == 0) {
      shared_callback()
    }
  }

  for (var i=0;i<async_calls.length;i++) {
    async_calls[i](callback);
  }
}

function setup_site_info(app, cb) {
  app.db.saveDoc({
    _id: "_local/site_info",
    name: "foo",
    site_id: 0
  }, {
    success: function(data) {
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
    success: function(data) {
      cb();
    }
  });
}
function setup_app(app, cb) {
  fork([
      function(cb) { setup_site_info(app, cb) },
      function(cb) { setup_bib_info(app, cb) }
  ], cb);
}


function test_bib_input(app) {
  setup_app(app, function() {
    module("bib_input"); 
    test("setup ok", function() {
        expect(1);
        ok(app.site_id != undefined, "undefined site id");
    });
    asyncTest("checkpoints insertion (with infos)", function() {
      var bib = 0;
      submit_bib(bib, app, function() {
        app.db.openDoc(checkpoints_id(bib, app.site_id), {
          success: function(checkpoints) {
            equal(checkpoints.site_id, app.site_id, "wrong site_id inserted");
            equal(checkpoints.times.length, 1, "wrong checkpoints times length");
            equal(checkpoints.race_id, 1, "wrong race_id inserted");
            start();
          },
          error: function() {
            ok(false, "Error getting freshly inserted checkpoint");
            start();
          }
        });
      });
    });
    asyncTest("checkpoints insertion (without infos)", function() {
      var bib = 999;
      submit_bib(bib, app, function() {
        app.db.openDoc(checkpoints_id(bib, app.site_id), {
          success: function(checkpoints) {
            equal(checkpoints.site_id, app.site_id, "wrong site_id inserted");
            equal(checkpoints.times.length, 1, "wrong checkpoints times length");
            equal(checkpoints.race_id, 0, "wrong race_id inserted");
            start();
          },
          error: function() {
            ok(false, "Error getting freshly inserted checkpoint");
            start();
          }
        });
      });
    });
  });
};
