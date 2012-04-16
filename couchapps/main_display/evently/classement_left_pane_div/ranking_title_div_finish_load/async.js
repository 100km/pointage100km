function(cb) {
  var app = $$(this).app;


    function cb2(params) {
	//$.log("cb2: " + JSON.stringify(params));
	cb(params);
    }



  app.db.list("main_display/global-ranking","global-ranking", {
    limit : 50,
  },{
    success: function(data) {
      cb2(data);
    }
  });
};

