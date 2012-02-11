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

    result.all_empty = all_empty;
    result.all_valid_messages = data;

    $.log("result " + JSON.stringify(result));
    
    return result;
}
