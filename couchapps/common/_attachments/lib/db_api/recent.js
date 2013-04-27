function db_recent(app, cb, site_id, limit) {
  site_id = site_id || app.site_id;
  // startkey and endkey are inversed because descending is true
  app.db.view("bib_input/recent-checkpoints", {
    descending: true,
    include_docs: true,
    limit : limit,
    startkey : [(site_id+1),0],
    endkey : [site_id,0],
    success: function(data) {
      _.each(data.rows, function(row) {
        if (row.doc) {
          row.infos = {
            first_name: row.doc.first_name,
            name: row.doc.name,
            race: row.doc.race
          };
        }
      });

      cb(data.rows);
    }
  });
}
