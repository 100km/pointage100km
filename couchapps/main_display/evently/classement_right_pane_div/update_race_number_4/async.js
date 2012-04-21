function(cb) {
  var app = $$(this).app;

  app.db.list("main_display/global-ranking","global-ranking", {
    limit : 50,
  },{
    success: function(data) {
      cb(data);
    }
  });
};

