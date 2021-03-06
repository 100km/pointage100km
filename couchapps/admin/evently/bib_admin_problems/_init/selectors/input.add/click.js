function() {
  var site_id = parseInt($(this).attr('data-site_id'));
  var lap = parseInt($(this).attr('data-lap')) + 1;
  var bib = parseInt($(this).attr('data-bib'));
  var next_site = parseInt($(this).attr('data-next_site'));
  var next_time = parseInt($(this).attr('data-next_time'));
  var prev_site = $(this).attr('data-prev_site');
  var prev_time = $(this).attr('data-prev_time');
  var db = $$(this).app.db;
  var app = $$(this).app;

  db.openDoc('infos', {
    success: function(infos) {
      db.openDoc('contestant-' + bib, {
        success: function(constestant) {
          var prev_km;
          if (prev_time) {
            prev_time = parseInt(prev_time);
            prev_site = parseInt(prev_site);
            // TODO hardcoded number of sites
            prev_km = site_lap_to_kms(app, prev_site, prev_site == 6 ? (lap - 1) : lap);
          } else {
            prev_time = infos.start_times[constestant.race];
            prev_km = 0;
          }

          var next_km = site_lap_to_kms(app, next_site, next_site == 0 ? (lap + 1) : lap);
          var miss_km = site_lap_to_kms(app, site_id, lap);
          var time = (next_time - prev_time) / (next_km - prev_km) * (miss_km - prev_km) + prev_time;

          time = Math.round(time);
          if (!isTimestampValid(time)) {
            alert("Refusing to insert invalid timestamp in DB: " + time);
            return;
          }

          db.openDoc('checkpoints-' + site_id + '-' + bib, {
            success: function(checkpoints) {
              checkpoints.times = checkpoints.times || [];
              checkpoints.times.push(time);
              checkpoints.times.sort();
              checkpoints.artificial_times = checkpoints.artificial_times || [];
              checkpoints.artificial_times.push(time);
              checkpoints.artificial_times.sort();
              checkpoints.artificial_times = _.uniq(checkpoints.artificial_times);
              checkpoints.deleted_times = checkpoints.deleted_times
                ? $.grep(checkpoints.deleted_times, function(element) { return element != time; })
                : [];
              db.saveDoc(checkpoints);
              $('#bib_admin_problems').trigger('_init');
            },
            error: function() {
              // TODO use update function 'add-checkpoint'
              db.saveDoc({
                _id: 'checkpoints-' + site_id + '-' + bib,
                type: "checkpoint",
                bib: bib,
                race_id: constestant.race,
                site_id: site_id,
                artificial_times: [time],
                times: [time]
              });
              $('#bib_admin_problems').trigger('_init');
            }
          });
        },
      });
    },
  });
}

