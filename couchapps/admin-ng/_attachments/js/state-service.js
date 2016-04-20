function StateService($http, database, changesService, $rootScope) {

  $service = this;

  this.contestants = {};

  this.injectContestants = function($scope) {
    $scope.contestants = $service.contestants;
  };

  changesService.onChange($rootScope, {
    heartbeat: 30000, since: 0, filter: "_view", view: "common/all_contestants",
    include_docs: true
  }, function (change) {
    var contestant = change.doc;
    contestant.full_name = contestant.first_name + " " + contestant.name +
      " (bib " + contestant.bib + ")";
    $rootScope.$applyAsync(function() { $service.contestants[contestant.bib] = contestant; });
  });
}

angular.module("admin-ng")
        .service("stateService", StateService)
        .directive("contestant", function() {
          return {
            template: "{{contestants[bib] ? contestants[bib].full_name : 'Bib ' + bib}}",
            scope: { bib: "<" },
            controller: function($scope, stateService) {
              stateService.injectContestants($scope);
            }
          };
        });
