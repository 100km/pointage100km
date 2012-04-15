function(data) {
    var p = {};
    var res = [];
    var i = 0;

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
	var result = {};

	//$.log("bibs: " + JSON.stringify(data));

	//here we need to retrieve the info about each contestant
	result = data;

	return result;
    }

    data.rows.map(map_races);

    res.map(map_bib);

    p.count = i;
    p.items = res.map(map_bib);

    
    $.log("res: " + JSON.stringify(res));

    $.log("p: " + JSON.stringify(p));

    return p;

};
