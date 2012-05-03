function(data) {
  data.empty_names = (data.names == undefined) || (data.names.length == 0);
  data.overflow = data.names && data.names.length == 10;
  return data;
}
