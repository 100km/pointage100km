function AnalysisListController($scope, $http, database) {
  var $ctrl = this;
  $http.get(database + "/_design/admin-ng/_view/by-anomalies")
    .then(function(response) {
      $scope.anomalies = response.data.rows.map(function(o) { return o.value; });
    });
}

angular.module("admin-ng").component("analysisList", {
  templateUrl: "partials/analysis-list.html",
  controller: AnalysisListController
});

function AnalysisController($scope, dbService) {
  var $ctrl = this;
  $scope.needsFixing = false;

  this.$routerOnActivate = function(next, previous) {
    $ctrl.bib = Number(next.params.bib);

    dbService.infos.then(function(infos) { $scope.infos = infos; });
    dbService.enrichedAnalysis($ctrl.bib).then(function(analysis) {
      $scope.analysis = analysis;
      $scope.needsFixing = analysis.anomalies > 0;
    });
  };
}

angular.module("admin-ng").component("analysis", {
  templateUrl: "partials/analysis.html",
  controller: AnalysisController,
  bindings: {
    bib: '<'
  }
});

function AnalysisSummaryController($scope) {
  var $ctrl = this;
}

angular.module("admin-ng").component("analysisSummary", {
  templateUrl: "partials/analysis-summary.html",
  controller: AnalysisSummaryController,
  bindings: {
    bib: '<',
    points: '<',
    analysis: '<',
    active: '<',
    panelid: '<'
  }
});

angular.module("admin-ng").controller("analysisPoint", ["$scope", "dbService",
    function($scope, dbService) {
      $scope.site = "Site " + $scope.point.site_id;
      $scope.action_icon = "trash";
      $scope.action_label = "Remove";
      $scope.action_class = "danger";
      if ($scope.point.action === "add") {
        $scope.action_label = "Add";
        $scope.action_icon = "plus-sign";
        $scope.action_class = "success";
      } else if ($scope.point.action === undefined) {
        $scope.action_class = "default";
      }
      dbService.infos.then(function(infos) {
        $scope.site = infos.sites[$scope.point.site_id] + " (" + $scope.point.site_id + ")";
      });
    }]);

angular.module("admin-ng").component("analysisTop", {
  template: "<ng-outlet></ng-outlet>",
  $routeConfig: [
  {path: "/", component: "analysisList", useAsDefault: true},
  {path: "/:bib", name: "Analysis", component: "analysis"}
  ]
});
