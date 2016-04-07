function(data) {
    res = data;
    
    var all_empty = true;
    for (i in res) {
	//$.log("data.js: data[" + i + "] => target=" + res[i].target + " message='" + res[i].message + "'");
	
	var empty = res[i].length == 0;
	res[i+"?"] = !empty;
	all_empty =  all_empty && empty;
    }
    
    var result = {};

    function reformat_data_thru_map(current) {
	res = {};

	res.target      = current.target || -1;
	res.message     = current.message;
	res.addedTS     = current.addedTS;
	res.time_hour   = time_to_hour_string(res.addedTS);
	res.message_id  = current._id;
	res.message_rev = current._rev;
	
	return res;
    }

    result.all_empty = all_empty;
    result.all_valid_messages = data.map(reformat_data_thru_map);

    //$.log("result " + JSON.stringify(result));
    
    return result;
}
