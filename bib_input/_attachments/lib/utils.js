function call_with_race_id(bib, app, f) {
  app.db.view("bib_input/bib_info", {
    key: bib,
    success: function(data) {
      //0 is invalid as a race_id
      //valid race_ids are 1,2,4,8
      var race_id = (data["rows"][0] && data["rows"][0]["value"] && data["rows"][0]["value"]["course"]) || 0;
      f(race_id);
    }
  });
}

function call_with_checkpoints(bib, app, f) {
  app.db.view("bib_input/contestant-checkpoints", {
    key: [app.site_id, bib],
    success: function(data) {
      var checkpoints = (data["rows"][0] && data["rows"][0]["value"]) || {};
      f.call(null, checkpoints);
    }
  });
}

function new_checkpoints(bib, race_id, site_id) {
  var checkpoints = {};
  checkpoints.bib = bib;
  checkpoints.race_id = race_id;
  checkpoints.site_id = site_id;
  checkpoints.times = [];
  return checkpoints;
}

function add_checkpoint(checkpoints) {
  var ts = new Date().getTime();
  checkpoints["times"].push(ts);
}

function remove_checkpoint(checkpoints, ts) {
  $.log("removing " + ts + " in " + checkpoints["times"]);
  //Why doesn't indexOf work ?!?
  for (var i = checkpoints["times"].length-1; i>=0; i--) {
    if (ts == checkpoints["times"][i]) {
      checkpoints["times"].splice(i, 1);
      return;
    }
  }
}

function isBib(bib)
{
  var isBib_re       = /^[1-9]\d*$/;
  return String(bib).search (isBib_re) != -1
}

function place_arrow(obj) {
  var app = $$(this).app;
  var $_arrow=$("#arrow");
  var pos = obj.offset();

  if (pos == null)
    return;

  var x_offset = obj.width();
  var y_offset = (obj.height()-42)/2+4; // +4 because of the border
  pos.top = pos.top + y_offset;
  pos.left = pos.left + x_offset;

  // Set the div to link concurrent with its infos
  $_arrow.css({ position: "absolute",
                marginLeft: 0, marginTop: 0,
                top: pos.top, left: pos.left });
  $_arrow.show();
}

function empty_info() {
        return {nom:"", prenom:""};
}

function pad2(number) {
  return (number < 10 ? '0' : '') + number
}

function time_to_hour_string(t) {
  date = new Date(t);
  return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
}
