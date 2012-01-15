function setup_app(app, cb) {
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

function test_bib_input(app) {
  setup_app(app, function() {
    module("bib_input"); 
    test("setup ok", function() {
        expect(1);
        ok(app.site_id != undefined, "undefined site id");
    });
    asyncTest("checkpoints insertion", function() {
      expect(2);
      var bib = 1;
      submit_bib(bib, app, function() {
        app.db.openDoc(checkpoints_id(bib, app.site_id), {
          success: function(checkpoints) {
            equal(checkpoints.site_id, app.site_id, "wrong site_id_inserted");
            equal(checkpoints.times.length, 1, "wrong checkpoints times length");
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
