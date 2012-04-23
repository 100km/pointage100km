function(data) {
  var p;
  var app = $$(this).app;
  if (data[0] == undefined) {
    return {
      item_0 : [],
      items : [],
    }
  }

  $.log("\n\nbottom_pane_data data: " + JSON.stringify(data));

  function search_twitter(query, div_to_update) {
    //based on code from: http://webhole.net/2009/11/28/how-to-read-json-with-javascript/
    var url='http://search.twitter.com/search.json?callback=?&q=';

    if (query == undefined) {
      $.log("undefined query!");
      return false;
    }

    query = escape(query);

    $.log("search twitter: '" + query + "'");

    function parse_search_result(json) {

      for (var i = 0; i < json.results.length; i++) {
        tweet = json.results[i];

        //only process the first 3 elements of the results
        if (i == 3) {
          break;
        }

        //TODO: find a replacement for "append" as I think it will not work well for db updates, maybe do horizontal scrolling?
        $(div_to_update).append('<p><img src="' + tweet.profile_image_url + '" width="48" height="48" />' + tweet.text + '</p>');

        //$.log("tweet[" + i + "]: " + tweet.text);
      }
    }

    $.getJSON(url+query, parse_search_result);
  }

  search_twitter("#paris", "#tweet_results_for_bib_81");

  //search_twitter("#paris", "#results");


  function get_messages(bib) {
    var r = {};
    var messages = new Array();
    messages[0] = "toto1";
    messages[1] = "toto2";
    messages[2] = "toto3";

    r.message_count = 3;
    r.messages = messages;
    return r;
  }

  function process_messages_for_bib(bib) {
    //this function should be calling search_twitter() for the given bib
    //however, since getJSON is asynchronous, without synchronising all the calls to search_twitter (with utils.js:fork()?), this cannot work

    var r = {};
    r = get_messages(bib);

    $.log("process_messages_for_bib " + bib);
    $.log("r = " + JSON.stringify(r));
  }


  // Return the data to display on item line.
  function create_infos(r) {
    p = {};
    p.bib = r.value && r.value.bib;
    p.lap = r.value && r.value.lap;
    p.ts  = r.key[1];
    p.time_hour = time_to_hour_string(p.ts);

    p.nom = r.infos && r.infos.nom;
    p.prenom = r.infos && r.infos.prenom;
    p.course = r.infos && r.infos.course;

    //p.messages = process_messages_for_bib(p.bib);

    return p;
  }

  return {
    items : data.map(create_infos)
  }


};
