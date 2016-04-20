angular.module("admin-ng").factory("stateService",
    ["$http", "database", "changesService", "$rootScope",
    function($http, database, changesService, $rootScope) {

      var data = {
        contestants: {},
        analyses: {}
      };

      changesService.initThenOnChange($rootScope, "common", "all_contestants",
          function (change) {
            var contestant = change.doc;
            $rootScope.$applyAsync(function() { data.contestants[contestant.bib] = contestant; });
          });

      changesService.initThenOnChange($rootScope, "admin-ng", "by-anomalies",
          function (change) {
            var analysis = change.doc;
            $rootScope.$applyAsync(function() { data.analyses[analysis.bib] = analysis; });
          });

      changesService.onChange($rootScope, {
        heartbeat: 30000, since: 0, filter: "_doc_ids", doc_ids: '["infos"]', include_docs: true
      }, function (change) {
        $rootScope.$applyAsync(function() { data.infos = change.doc; });
      });

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
