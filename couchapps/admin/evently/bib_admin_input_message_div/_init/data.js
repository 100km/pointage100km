function(data) {
  return {
    sites: _.map(data.sites, function(el, idx) {
      return {site_id: idx, site_name: el};
    })
  };
}
