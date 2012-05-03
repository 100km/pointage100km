function(data) {
  data.empty_names = (data.names == undefined) || (data.names.length == 0);
  return data;
}
