
function query_twitter(query, div_to_update, bib) {
    var url='http://search.twitter.com/search.json?callback=?&q=';
    var max_results_per_request = 3;
    var query_params = '&rpp='+max_results_per_request+'&result_type=recent';
    
    if (query == undefined) {
	$.log("undefined query!");
	return false;
    }
    
    query = escape(query);
    
    $.log("2 search twitter: '" + query + "', params='" + query_params + "'");
    
    function parse_search_result(json) {
	var marquee_start_tag = '<marquee behavior="scroll" scrollamount="3" direction="left">';
	var marquee_end_tag   = '</marquee>';
	var marquee_tag = '';
	
	marquee_tag = marquee_start_tag;
	
	for (var i = 0; i < json.results.length; i++) {
	    tweet = json.results[i];
	    
	    //only process the first 3 elements of the results
	    if (i == 3) {
		break;
	    }
	    
	    marquee_tag += tweet.text + '<i> ' + time_to_hour_string(tweet.created_at) + ' </i> | '
	    
	    $.log("tweet[" + i + "]: " + tweet.text);	
	}
	
	marquee_tag += marquee_end_tag;
	
	$.log("done parsing search results for bib=" + bib);
	
	$(div_to_update + ' p').replaceWith(marquee_tag);
	
	function betterMarquee(marquee_to_update) {
	    // Replace the marquee and do some fancy stuff (taken from remy sharp's website)
	    $(marquee_to_update + ' marquee').marquee('pointer')
		.mouseover(function () {
		    $(this).trigger('stop');
		})
		.mouseout(function () {
		    $(this).trigger('start');
		})
		.mousemove(function (event) {
		    if ($(this).data('drag') == true) {
			this.scrollLeft = $(this).data('scrollX') + ($(this).data('x') - event.clientX);
		    }
		})
		.mousedown(function (event) {
		    $(this).data('drag', true).data('x', event.clientX).data('scrollX', this.scrollLeft);
		})
		.mouseup(function () {
		    $(this).data('drag', false);
		});
	}
	
	betterMarquee(div_to_update);
    }
    
    $.getJSON(url+query+query_params, parse_search_result);
}
