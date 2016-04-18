angular.module("admin-ng").controller("livenessCtrl",
    ["$scope", "$http", "database", "$interval", "dbService",
    function($scope, $http, database, $interval, dbService) {
      $scope.liveness = [];
      $scope.times = [];

      var checkSites = function() {
        $http.get(database + "/_design/admin/_view/alive?group_level=1")
          .then(function(response) {
            var alive = response.data;
            var now = Number(new Date());
            angular.forEach(alive.rows, function(row) {
              var t = row.value.max;
              var d = (now - t) / 60000;
              var state;
              if (d < 5) state = "success";
              else if (d < 15) state = "info";
              else if (d < 30) state = "warning";
              else state = "danger";
              $scope.liveness[row.key] = state;
              $scope.times[row.key] = t;
            });
          });
      };

      dbService.infos.then(function(infos) {
        $scope.infos = infos;
        checkSites();
        var check = $interval(checkSites, 30000);
        $scope.$on("$destroy", function() {
          $interval.cancel(check);
        });
      });

    }]);

angular.module("admin-ng").component("liveness", {
  templateUrl: "partials/liveness.html",
  controller: "livenessCtrl",
  bindings: {
    "infos": "<"
  }
})
