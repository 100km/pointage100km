function(data) {

  function format_date(time) {
    date = new Date(time);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  for (var i=0; i<data.pbs.length ;i++) {
    for (var j=0; j<data.pbs[i].sites.length; j++) {
      for (var k=0; k<data.pbs[i].sites[j].times.length; k++) {
        data.pbs[i].sites[j].times[k].formatted_val = format_date(data.pbs[i].sites[j].times[k].val);
      }
      for (var k=0; k<data.pbs[i].sites[j].artificial_times.length; k++) {
        data.pbs[i].sites[j].artificial_times[k].formatted_val = format_date(data.pbs[i].sites[j].artificial_times[k].val);
      }
      for (var k=0; k<data.pbs[i].sites[j].deleted_times.length; k++) {
        data.pbs[i].sites[j].deleted_times[k].formatted_val = format_date(data.pbs[i].sites[j].deleted_times[k].val);
      }
    }
    for (var j=0; j<data.pbs[i].times.length; j++) {
      data.pbs[i].times[j].formatted_time = format_date(data.pbs[i].times[j].time);
    }
  }

  // Return the data.
  return data;
};
