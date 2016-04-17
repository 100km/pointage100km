function AnalysisController($scope, dbService) {
  var $ctrl = this;
  $scope.needsFixing = false;
  dbService.infos.then(function(infos) { $scope.infos = infos; });
  dbService.enrichedAnalysis($ctrl.bib).then(function(analysis) {
    $scope.analysis = analysis;
    $scope.needsFixing = analysis.anomalies > 0;
  });
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
    active: '<'
  }
});

angular.module("admin-ng").controller("analysisPoint", ["$scope", "dbService",
    function($scope, dbService) {
      $scope.site = "Site " + $scope.point.site_id;
      dbService.infos.then(function(infos) {
        $scope.site = $scope.site + " (" + infos.sites[$scope.point.site_id] + ")";
      });
    }]);
