function(data) {
    var p;

    //sample code from: http://webhole.net/2009/11/28/how-to-read-json-with-javascript/ 
    var url='http://search.twitter.com/search.json?callback=?&q=';
    var query;

    function query_twitter() {
	query = "#paris"; //$("#query").val();
	query = escape(query);
	
	$.log("querying twitter with: " + url + query);
	
	$.getJSON(url+query, function(json) {
	    $.each(json.results, function(i, tweet) {
		$("#results").append('<p><img src="'+tweet.profile_image_url+'" widt="48" height="48" />'+tweet.text+'</p>');
	    });
	});
    }

    query_twitter();
    
    return {
	races: data.rows.map(function(pair) {
	    return {
		race_id: pair.race_id,
		items: pair.contestants.map(function(r) {
		    p = {};
		    p.bib = r.value && r.value.bib;
		    p.lap = r.value && r.value.times.length;
		    return p;
		})
	    }
	})
    }
};
