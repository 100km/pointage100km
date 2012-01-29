function(data) {
  res = data
  var all_empty = true;
  for (i in res) {
    var empty = res[i].length == 0;
    res[i+"?"] = !empty;
    all_empty =  all_empty && empty;
  }


  // TODO: see if there is other way to check for emptyness of JSON
  if (all_empty) {
    $("#message-bar").hide();
  }
  else
    $("#message-bar").show();

  return res;
}
