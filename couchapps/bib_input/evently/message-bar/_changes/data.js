function(data) {
  var all_empty = true;
  for (i in data) {
    var empty;
    $.log("in " + i);
    if (data[i].message != undefined) {
      //Single object with message
      empty = !data[i].message.length > 0;
    } else {
      //array of objects with messages
      empty = !_.any(data[i], function(el) { return el.message.length > 0 });
    }
    data[i+"?"] = !empty;
    all_empty =  all_empty && empty;
  }

  data.all_empty = all_empty;
  $.log("final");
  $.log(data);

  return data;
}
