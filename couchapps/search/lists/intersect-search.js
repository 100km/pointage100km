function(head, req) {

  function search_nonmatch_field(str) {
    if (str == "name")
      return "first_name"
    else
      return "name";
  }

  function remove_accents(str) {
    var changes = [
      { base: "a", letters: /[àáâãäå]/g },
      { base: "e", letters: /["éèêë"]/g },
      { base: "i", letters: /[ìíîï]/g },
      { base: "o", letters: /["òóôõö"]/g },
      { base: "u", letters: /[ùúûü]/g },
      { base: "y", letters: /[ýÿ]/g },
      { base: "oe", letters: /œ/g },
      { base: "ae", letters: /æ/g },
      { base: "c", letters: /ç/g },
      { base: "n", letters: /ñ/g }
    ];
    for(var i=0; i<changes.length; i++) {
      str = str.replace(changes[i].letters, changes[i].base);
    }
    return str;
  }

  start({
    "headers": {
      "Content-Type": "application/json"
    }
  });

  var other_term = req.query.term;
  var limit = req.query.my_limit;
  var row;
  var arr = [];
  var i = 0;
  while(row = getRow()) {
    var add;
    if (other_term == undefined) {
      add = true;
    } else {
      var comp  = remove_accents(row.value[search_nonmatch_field(row.value.match)]).toLowerCase().substr(0, other_term.length);
      var other = remove_accents(other_term).toLowerCase();
      add = comp == other;
    }

    if (add) {
      arr.push(row);
      i++;
      if (limit && i==limit)
        break;
    }
  }

  var res = { rows: arr };
  return JSON.stringify(res);
}
