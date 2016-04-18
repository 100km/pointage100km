angular.module("admin-ng").controller("livenessCtrl",
    ["$scope", "$interval", "$http", "database", "changesService", "dbService",
    function($scope, $interval, $http, database, changesService, dbService) {
      $scope.liveness = [];
      $scope.times = [];

      // Set a site timestamp. If the new timestamp is not greater or equal
      // (equal is useful to refresh the status), it is ignored.
      $scope.setSite = function(siteId, timestamp) {
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
        $scope.liveness[siteId] = state;
        $scope.times[siteId] = timestamp;
      };

      // Load the infos into the scope to get the site names.
      dbService.infos.then(function(infos) {
        $scope.infos = infos;
      });

      // Initially check the sites liveness to get fresh information as soon as
      // the page is loaded.
      $scope.checkSites = function() {
        return $http.get(database + "/_design/admin/_view/alive?group_level=1")
          .then(function(response) {
            var alive = response.data;
            angular.forEach(response.data.rows, function(row) {
              $scope.setSite(row.key, row.value.max);
            });
          });
      };

      // Use a _changes connection to get fresh information about the sites.
      $scope.checkSites().then(function() {
        changesService.onChange($scope, {since: "now", heartbeat: 30000, include_docs: true,
          filter: "_view", view: "admin/alive"},
          function(change) {
            $scope.setSite(change.doc.site_id, change.doc.time);
          });
      });

      // Regularly refresh the informations we have (with the same timestamps)
      // in order to refresh the display in case the status has changed.
      var periodic = $interval(function() {
        for (var siteId in $scope.times) {
          if (isFinite($scope.times[siteId])) {
            $scope.setSite(siteId, $scope.times[siteId]);
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
