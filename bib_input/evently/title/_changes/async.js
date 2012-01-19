function(cb) {
  var app = $$(this).app;

  function title_cb (result) {
    cb({name:result[0][0]["name"], site_id:result[0][0]["site_id"], sites:result[1][0]["sites"]});
  }

  fork([
    function(cb) { get_doc(app, cb, "_local/site_info") },
    function(cb) { get_doc(app, cb, "infos") }
  ], title_cb);

}

