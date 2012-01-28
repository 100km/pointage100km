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
  return res;
}
