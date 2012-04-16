function(cb) {
    var i = 0;
    var app = $$(this).app;
    
    function unwrap_data(data) {
	return data.rows.map(function(row) {
	    return row.value;
	});
    }

    function get_all_contestants(app, cb1) {
	app.db.view("common/all_contestants", {
	    success: function(data) {
		cb1(unwrap_data(data));
	    }
	});
    }

    function map_contestants(data) {
	var result = {};

	//$.log("map_contestants: " + JSON.stringify(data));
	
	//no error checking: we suppose all contestants have the following info in the database
	result.dossard = data.dossard;
	result.nom     = data.nom;
	result.prenom  = data.prenom;
	result.course  = data.course;

	return result;
    }

    function cb2(params) {
	var result = {};

	//$.log("cb2: " + JSON.stringify(params));

	result = params.map(map_contestants);

	//$.log("result: " + JSON.stringify(result));

	//sink
	cb(result);
    }

    get_all_contestants(app, cb2);

}

