angular.module("admin-ng").factory("stateService",
    ["$http", "database", "changesService", "$rootScope",
    function($http, database, changesService, $rootScope) {

      var data = {
        contestants: {},
        analyses: {},
        installInfos: function(scope) {
          return changesService.installAndCheck(scope, "infos", "infos");
        }
      };

      changesService.initThenFilterEach($rootScope, "common", "all_contestants",
          function(change) { return change.doc.type === "contestant"; },
          function(row) { data.contestants[row.doc.bib] = row.doc; });

      changesService.initThenFilterEach($rootScope, "admin-ng", "by-anomalies",
          function(change) { return change.doc.type == "analysis"; },
          function(row) { data.analyses[row.doc.bib] = row.doc; });

      return data;

}]);

angular.module("admin-ng")
        .directive("contestant", function() {
          return {
            template: "{{contestantName}}",
            scope: { bib: "<" },
            controller: function($scope, stateService) {
              this.$onInit = function() {
                $scope.$watchGroup([function() { return stateService.contestants[$scope.bib]; },
                                    "bib"],
                    function(values) {
                      var contestant = values[0];
                      if (contestant)
                        $scope.contestantName = contestant.first_name + " " + contestant.name +
                          " (bib " + contestant.bib + ")";
                      else
                        $scope.contestantName = "Bib " + $scope.bib;
                    });
              };
            }
          };
        })
        .directive("race", function() {
          return {
            template: "{{raceName}}",
            scope: { raceId: "<" },
            controller: function($scope, stateService) {
              this.$onInit = function() {
                $scope.$watchGroup([function() { return stateService.infos; },
                                    "raceId"],
                    function(values) {
                      var infos = values[0];
                      var raceId = values[1];
                      if (infos)
                        $scope.raceName = infos.races_names[raceId];
                      else
                        $scope.raceName = "Race " + raceId;
                    });
              };
            }
          };
        });
