function (datas) {
  if (typeof(datas.site_id) == "undefined")
  {
    console.log("site_id not initialized", datas);
    return;
  }
  if(! (datas.data && (datas.data.length > 0)))
  {
    console.log("no data in datas", datas);
    return;
  }

  var info = datas.data[0];
  // Build the data object for the checkpoint.
  var data;
  if (info.doc) {
    data = info.doc;
  } else {
    data = { warning: true };
  }
  data.bib = info.value.bib;
  data.lap = info.value.lap;
  data.ts = info.key[1];

  $(this).trigger('change_infos', data);
}
