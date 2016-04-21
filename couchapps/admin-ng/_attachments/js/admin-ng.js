var app = angular.module("admin-ng", ["ngComponentRouter", "ui.bootstrap"]);

app.controller("infosCtrl", ["$scope", "$http", "database", function($scope, $http, database) {
  $http.get(database + "/infos").then(function(response) { $scope.infos = response.data; });
}]);

app.controller("appCtrl", ["$scope", "$timeout", function($scope, $timeout) {
}]);

app.controller("reactiveChangesCtrl", ["$scope", "globalChangesService", function($scope, globalChangesService) {
  $scope.messages = [];
  $scope.$on("change", function(event, data) { $scope.messages.push(data); });
}]);

app.controller("siteCtrl", ["$scope", "$routeParams", "changesService", "$http", "database", "$interval",
    function($scope, $routeParams, ChangesService, $http, database, $interval) {
      $scope.siteId = Number($routeParams.siteId);
      $scope.checkpoints = [];
      var params = {startkey: JSON.stringify([$scope.siteId]), endkey: JSON.stringify([$scope.siteId + 1]), limit: 200,
        include_docs: true, update_seq: true}
      var latestSeq = 0
      var load = function() {
        $http.get(database + "/_design/admin-ng/_view/last-checkpoints", {params: params})
          .then(function(response) {
            var data = response.data;
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

app.filter("gravatarUrl", function() {
  return function(doc) {
    return "http://www.gravatar.com/avatar/" + CryptoJS.MD5(angular.lowercase(doc.email));
  };
});

app.filter("digitsUnit", ["$filter", function($filter) {
  return function(x, digits, unit) {
    return x === undefined ? "" : ($filter('number')(x, digits) + " " + unit);
  }
}]);

app.constant("database", "../../");
app.value("$routerRootComponent", "app");
app.component("app", {
  templateUrl: "partials/admin-ng.html",
  controller: "appCtrl",
  $routeConfig: [
    {path: "/analysis/...", name: "Analysis", component: "analysisTop", useAsDefault: true},
    {path: "/sms/", name: "SMS", component: "sent"},
    {path: "/alerts/", name: "Alerts", component: "alerts"},
  ]
});
app.run(["globalChangesService",
    function(globalChangesService) {
      globalChangesService.start();
    }]);
