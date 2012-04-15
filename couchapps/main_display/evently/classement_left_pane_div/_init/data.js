function(data) {
    var p = {};
    var res = [];
    var i = 0;
    var app = $$(this).app

    $.log("app.contestants: " + JSON.stringify(app.contestants));
    
    //$.log("data: " + JSON.stringify(data));

    function map_contestants(data) {
	var result = {};

	//$.log("contestants: " + JSON.stringify(data));

	if (data.value) {
	    result.bib = data.value.bib;
	    result.lap = data.value.times.length;

	    //unroll the "multi-row" stuff (don't understand the point of having it like that yet)
	    res.push(result);
	    i++;
	}

	return result;
    }

    function map_races(data) {
	var result = {};

	//$.log("races: " + JSON.stringify(data));

	//result.race_id = data.race_id;
	result = data.contestants.map(map_contestants);
	
	return result;
    }

    function map_bib(data) {
	var result = {
	    bib: 0,
	    dossard: 0,
	    nom: "",
	    prenom: "",
	    course: 0,
	    lap: 0,
	};

	$.log("bibs: " + JSON.stringify(data));

	//set known values
	result.dossard = data.bib;
	result.lap     = data.lap;

	if (app.contestants === undefined) {
	    $.log("contestants not loaded yet");
	}
	else {
	    var current_contestant = app.contestants[data.bib];

	    if (current_contestant === undefined) {
		$.log("skip contestant: " + data.bib);
	    }
	    else {
		result.nom     = current_contestant.nom;
		result.prenom  = current_contestant.prenom;
		result.course  = current_contestant.course;	
	    }
	}

	return result;
    }

    data.rows.map(map_races);

    p.count = i;
    p.items = res.map(map_bib);
    
    $.log("res: " + JSON.stringify(res));
    $.log("p: " + JSON.stringify(p));

    return p;

};
