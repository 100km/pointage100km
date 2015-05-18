var app = angular.module("app", ["ngRoute"]);

app.factory("onChangesService", ["database", "$httpParamSerializer", function(database, $httpParamSerializer) {
  return {
    onChanges: function(scope, params, callback) {
      var ev;
      var allParams = angular.merge({feed: "eventsource"}, params);

      var reconnect = function() {
        ev = new EventSource(database + "/_changes?" + $httpParamSerializer(allParams));
        ev.onmessage = function(event) {
          allParams.since = event.lastEventId;
          scope.$applyAsync(function() { callback(JSON.parse(event.data)); });
        };
        ev.onerror = function(event) {
          if (ev.readyState === EventSource.CLOSED) {
            ev.close();
            reconnect();
          }
        }
      }

      reconnect();
      scope.$on("$destroy", function() { ev.close(); });
    }
  };
}]);

app.factory("globalChangesService", ["onChangesService", "$rootScope", "database", "$http",
    function(onChangesService, $rootScope, database, $http) {
      return {
        start: function() {
          $http.get(database).success(function(status) {
            var sequence = status.update_seq;
            onChangesService.onChanges($rootScope, {since: sequence, heartbeat: 3000, include_docs: true},
                function(change) {
                  $rootScope.$broadcast("change", change);
                });
          });
        }
      };
    }]);

app.controller("infosCtrl", ["$scope", "$http", "database",
    function($scope, $http, database) {
      $http.get(database + "/infos").success(function(infos) {
        $scope.infos = infos;
      });
    }]);

app.controller("livenessCtrl", ["$scope", "$http", "database", "$interval", function($scope, $http, database, $interval) {
  $scope.liveness = [];
  $scope.times = [];
  var resetLiveness = function() {
    $scope.liveness = [];
    for (var siteId in $scope.infos.sites) {
      $scope.liveness.push("default");
    }
  };
  var checkSites = function() {
    $http.get(database + "/_design/admin/_view/alive?group_level=1")
      .success(function(alive) {
        var now = Number(new Date())
          resetLiveness();
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
      })
  };
  $scope.$watch("infos", function(infos) {
    if (infos) {
      resetLiveness();
      checkSites();
      var checker = $interval(checkSites, 10000);
      $scope.$on("$destroy", function() { $interval.cancel(checker); });
    }
  });
}]);

app.controller("reactiveChangesCtrl", ["$scope", "globalChangesService", function($scope, onChangesService) {
  $scope.messages = [];
  $scope.$on("change", function(event, data) { $scope.messages.push(data); });
}]);

app.controller("siteCtrl", ["$scope", "$routeParams", "onChangesService", "$http", "database", "$interval",
    function($scope, $routeParams, onChangesService, $http, database, $interval) {
      $scope.siteId = Number($routeParams.siteId);
      $scope.checkpoints = [];
      var params = {startkey: JSON.stringify([$scope.siteId]), endkey: JSON.stringify([$scope.siteId + 1]), limit: 200,
        include_docs: true, update_seq: true}
      var latestSeq = 0
      var load = function() {
        $http.get(database + "/_design/admin-ng/_view/last-checkpoints", {params: params})
          .success(function(data) {
            latestSeq = data.update_seq;
            $scope.checkpoints = [];
            var latest;
            angular.forEach(data.rows, function(row) {
              if (row.doc != null && row.doc.type == "checkpoint") {
                $scope.checkpoints.push(row.doc);
                latest = row.doc;
                latest.time = -row.key[1];
                if (latest.times.indexOf(latest.time) != -1)
                  latest.time_type = "regular";
                else if ((latest.artificial_times || []).indexOf(latest.time) != -1)
                  latest.time_type = "artificial";
                else
                  latest.time_type = "deleted";
                latest.contestant_type = "unknown";
              } else if (row.doc != null) {
                angular.extend(latest, row.doc);
                latest.contestant_type = "regular";
              }
            });
          });
      };
      var checkpointPrefix = "checkpoints-" + $scope.siteId + "-";
      $scope.$on("change", function(event, change) {
        if (change.seq > latestSeq && change.id.startsWith(checkpointPrefix))
          load();
      });
      load();
    }]);

app.filter("fullName", function() {
  return function(doc) {
    if (doc.name)
      return doc.first_name + " " + doc.name + " (dossard " + doc.bib + ")";
    else
      return "Dossard " + doc.bib;
  };
});

app.constant("database", "http://localhost:5984/backup");
app.config(["$routeProvider",
    function($routeProvider) {
      $routeProvider.
        when("/changes", {
          templateUrl: "partials/changes.html",
          controller: "reactiveChangesCtrl"
        }).
      when("/site/:siteId", {
        templateUrl: "partials/site.html",
        controller: "siteCtrl",
        controllerAs: "site"
      }).
      otherwise({
        redirectTo: "/changes"
      });
    }]);
app.run(["globalChangesService",
    function(globalChangesService) {
      globalChangesService.start();
    }]);
