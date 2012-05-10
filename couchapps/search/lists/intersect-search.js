function(head, req) {

  function search_nonmatch_field(str) {
    if (str == "nom")
      return "prenom"
    else
      return "nom";
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
      "Content-Type": "text/plain"
    }
  });

  var other_term = req.query.term;
  var limit = req.query.my_limit;
  var regexp;
  if (other_term != undefined) {
    regexp = new RegExp(remove_accents(other_term), "i");
  }
  var row;
  var arr = [];
  var i = 0;
  while(row = getRow()) {
    if (regexp == undefined || regexp.test(remove_accents(row.value[search_nonmatch_field(row.value.match)]))) {
      arr.push(row);
      i++;
      if (limit && i==limit)
        break;
    }
  }

  var res = { rows: arr };
  return JSON.stringify(res);
}
