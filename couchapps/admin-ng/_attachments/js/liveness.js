function LivenessController($scope, $interval, dbService, changesService, stateService) {
  $scope.liveness = [];
  $scope.times = [];

  // Set a site timestamp. If the new timestamp is not greater or equal
  // (equal is useful to refresh the status), it is ignored.
  this.setSite = (siteId, timestamp) => {
    if (isFinite($scope.times[siteId]) && $scope.times[siteId] > timestamp) {
      return;
    }
    var now = Number(new Date());
    var d = (now - timestamp) / 60000;
    var state;
    if (d < 5) state = "success";
    else if (d < 15) state = "info";
    else if (d < 30) state = "warning";
    else state = "danger";
    $scope.$applyAsync(() => {
      $scope.liveness[siteId] = state;
      $scope.times[siteId] = timestamp;
    });
  };

  // Load the infos into the scope to get the site names.
  stateService.installInfos($scope);

  // Initially check the sites liveness to get fresh information as soon as
  // the page is loaded. Return the sequence number in a promise.
  this.checkSites = () => {
    return dbService.checkSites()
      .then(data => {
        angular.forEach(data.rows, row => this.setSite(row.key, row.value.max));
        return changesService.toSeqNumber(data.update_seq);
      });
  };

  // Watch for fresh information about the sites once the initial information
  // has arrived.
  changesService.filterChangesAfter($scope,
      change => change.doc.type === "ping" || change.doc.type === "checkpoint",
      change => {
        var time;
        var doc = change.doc;
        if (doc.type === "ping")
          this.setSite(doc.site_id, doc.time);
        else {
          var times = doc.times || [];
          var artificial_times = doc.artificial_times || [];
          var i = times.length - 1;
          while (i >= 0) {
            if (artificial_times.indexOf(times[i]) === -1) {
              this.setSite(doc.site_id, times[i]);
              break;
            }
            i--;
          }
        }
      },
      this.checkSites());

  // Regularly refresh the informations we have (with the same timestamps)
  // in order to refresh the display in case the status has changed.
  var periodic = $interval(() => {
    for (var siteId in $scope.times) {
      if (isFinite($scope.times[siteId])) {
        this.setSite(siteId, $scope.times[siteId]);
      }
    }
  }, 10000);

  // Cancel the timer on scope exit.
  $scope.$on("$destroy", () => $interval.cancel(periodic));

}

angular.module("steenwerck.liveness", ["steenwerck.database", "steenwerck.state", "changes"])
  .component("liveness", {
    templateUrl: "partials/liveness.html",
    controller: LivenessController,
    bindings: {
      "infos": "<"
    }
  });
