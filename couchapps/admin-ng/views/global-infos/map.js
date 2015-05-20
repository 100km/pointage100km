function(doc) {
  if (doc._id === "infos") {
    var first_race = true;
    var max_laps = [].concat(doc.races_laps).sort().reverse()[0];
    emit("max_laps", max_laps);
    for (var race in doc.races_laps) {
      race = Number(race);
      var laps = doc.races_laps[race];
      for (var lap = 1; lap <= laps; lap++) {
        for (var site in doc.sites) {
          site = Number(site);
          var infos = {race_name: doc.races_names[race], start_time: doc.start_times[race], race_hours: doc.races_hours[race],
                       site_id: site, site_name: doc.sites[site], lap: lap, site_kms: doc.kms_lap * (lap - 1) + doc.kms_offset[site],
                       site_coordinates: doc.sites_coordinates[site]};
          emit(["race", race, lap, site], infos);
          if (first_race && laps === max_laps)
            emit(["site", site, lap], infos);
        }
      }
      first_race &= laps !== max_laps;
    }
  }
}
