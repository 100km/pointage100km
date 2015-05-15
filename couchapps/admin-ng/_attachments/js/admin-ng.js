var app = angular.module("app", []);

app.factory("onChangesService", ["database", function (database) {
    return {
        onChanges: function (scope, params, callback) {
            var ev = new EventSource(database + "/_changes?feed=eventsource" + (params ? ("&" + params) : ""));
            ev.onmessage = function (e) {
                scope.$apply(callback(e.data));
            };
            scope.$on("$destroy", function () {
                console.log("Closing event source");
                ev.close();
            });
        }
    };
}]);

app.controller("liveness", ["$scope", "$http", "database", "$interval", function ($scope, $http, database, $interval) {
    $scope.liveness = [];
    $scope.times = [];
    var resetLiveness = function () {
        $scope.liveness = [];
        for (var siteId in $scope.infos.sites) {
            $scope.liveness.push("default");
        }
    };
    var checkSites = function() {
        $http.get(database + "/_design/admin/_view/alive?group_level=1")
            .success(function (alive) {
            var now = Number(new Date())
            resetLiveness();
            angular.forEach(alive.rows, function (row) {
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
    $http.get(database + "/infos").success(function (infos) {
        $scope.infos = infos;
        resetLiveness();
        checkSites();
        var checker = $interval(checkSites, 10000);
        $scope.$on("$destroy", function () { cancel(); });
    });
}]);

app.controller("reactiveChanges", ["$scope", "onChangesService", function ($scope, onChangesService) {
    $scope.messages = [];
    onChangesService.onChanges($scope, "heartbeat=2000&since=7030&filter=bib_input/no-ping", function (e) {
        $scope.messages.push(e);
    });
}]);

app.constant("database", "http://localhost:5984/steenwerck100km");