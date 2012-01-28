function(data) {
  res = {};
  for (i in data.rows) {
    var row = data.rows[i];
    if (row.doc) {
    var id = normalize_message_id(row.id);
    res[id + "?"] = (row.doc.messages.length > 0);
    res[id] = row.doc.messages.map(
      function(message) {
        return { message: message };
      });
    }
  }

  // TODO: see if there is other way to check for emptyness of JSON
  if (JSON.stringify(res) == "{}") {
    $("#message-bar").hide();
  }
  else
    $("#message-bar").show();

  return res;
}
