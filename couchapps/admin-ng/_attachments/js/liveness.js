angular.module("admin-ng").controller("livenessCtrl",
    ["$scope", "$interval", "$http", "database", "changesService", "stateService",
    function($scope, $interval, $http, database, changesService, stateService) {
      var ctrl = this;

      $scope.liveness = [];
      $scope.times = [];

      // Set a site timestamp. If the new timestamp is not greater or equal
      // (equal is useful to refresh the status), it is ignored.
      this.setSite = function(siteId, timestamp) {
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
        $scope.$applyAsync(function() {
          $scope.liveness[siteId] = state;
          $scope.times[siteId] = timestamp;
        });
      };

      // Load the infos into the scope to get the site names.
      stateService.installInfos($scope);

      // Initially check the sites liveness to get fresh information as soon as
      // the page is loaded.
      this.checkSites = function() {
        return $http.get(database + "/_design/admin/_view/alive?group_level=1")
          .then(function(response) {
            var alive = response.data;
            angular.forEach(response.data.rows, function(row) {
              ctrl.setSite(row.key, row.value.max);
            });
          });
      };

      // Watch for fresh information about the sites
      this.checkSites().then(function() {
        changesService.filterChanges($scope,
            function(change) {
              return change.doc.type === "ping" || change.doc.type === "checkpoint";
            },
            function(change) {
              var time;
              var doc = change.doc;
              if (doc.type === "ping")
                ctrl.setSite(doc.site_id, doc.time);
              else {
                var times = doc.times || [];
                var artificial_times = doc.artificial_times || [];
                var i = times.length - 1;
                while (i >= 0) {
                  if (artificial_times.indexOf(times[i]) === -1) {
                    ctrl.setSite(doc.site_id, times[i]);
                    break;
                  }
                  i--;
                }
              }
            });
      });

      // Regularly refresh the informations we have (with the same timestamps)
      // in order to refresh the display in case the status has changed.
      var periodic = $interval(function() {
        for (var siteId in $scope.times) {
          if (isFinite($scope.times[siteId])) {
            ctrl.setSite(siteId, $scope.times[siteId]);
          }
        }
      }, 10000);

      // Cancel the timer on scope exit.
      $scope.$on("$destroy", function() { $interval.cancel(periodic); });

    }]);

angular.module("admin-ng").component("liveness", {
  templateUrl: "partials/liveness.html",
  controller: "livenessCtrl",
  bindings: {
    "infos": "<"
  }
})
