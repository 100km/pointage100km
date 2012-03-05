function(cb) {
    var app = $$(this).app;

    $.log("here!");

    function _unwrap_messages(data) {
	return data.rows.map(function(row) {
	    //$.log("rows: " + row.value);
	    return row.value;
	});
    }
    function _get_messages(app, startkey, endkey, cb1) {
	app.db.view("admin/all-valid-messages", {
	    startkey: startkey,
	    endkey:   endkey,
	    success: function(data) {
		cb1(unwrap_messages(data));
	    }
	});
    }
    
    //get all valid messages
    _get_messages(app, [true], [1], cb);



  //call_with_messages(app, cb);
}
