function(data) {
  if (data.length == 1)
    $(this).trigger("single-contestant", data[0]);
  else
    $(this).trigger("multiple-contestants", {names:data});
}
