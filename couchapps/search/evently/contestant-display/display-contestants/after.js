function(data) {
  if (data.length == 1)
    $(this).trigger("single-contestant", data[0].value);
  else
    $(this).trigger("multiple-contestants", {request: data.request, names:data.map(function(row) { return row.value })});
}
