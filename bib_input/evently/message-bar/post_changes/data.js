function(data) {
  res = data
  var all_empty = true;
  for (i in res) {
    var empty = res[i].length == 0;
    res[i+"?"] = !empty;
    all_empty =  all_empty && empty;
  }

  res.all_empty = all_empty

  return res;
}
