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

function AnalysisController($scope, dbService, changesService) {
  var $ctrl = this;
  $scope.needsFixing = false;

  $scope.loadAnalysis = function() {
    dbService.enrichedAnalysis($scope.bib).then(function(analysis) {
      $scope.analysis = analysis;
      $scope.needsFixing = analysis.anomalies > 0;
    });
  };

  this.$routerOnActivate = function(next, previous) {
    $scope.bib = Number(next.params.bib);

    dbService.infos.then(function(infos) { $scope.infos = infos; });
    $scope.loadAnalysis();

    // If either the document or the contestant information changes,
    // we want to reload a fresh analysis.
    changesService.onChange($scope,
        {filter: "_doc_ids", doc_ids: '["contestant-' + $scope.bib + '","analysis-' + $scope.bib + '"]',
          heartbeat: 30000, /* since: "now" */},
          $scope.loadAnalysis);
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
      var setAction = function(label, icon, clazz) {
        $scope.action_label = label;
        $scope.action_icon = icon;
        $scope.action_class = clazz;
      };
      $scope.site = "Site " + $scope.point.site_id;
      $scope.action_label = "Remove";
      $scope.action_icon = "trash";
      $scope.action_class = "danger";
      if ($scope.point.action === "add") {
        setAction("Add", "plus-sign", "success");
        $scope.tooltip = "The algorithm suggests to add this point";
      } else if ($scope.point.action === "remove") {
        setAction("Remove", "trash", "danger");
        $scope.tooltip = "The algorithm suggests to delete this point";
      } else if ($scope.point.type === "deleted") {
        setAction("Restore", "plus-sign", "deleted");
        $scope.tooltip = "This time has been previously deleted";
      } else if ($scope.point.type === "down") {
        setAction(null, null, "down");
      } else if ($scope.point.type === "artificial") {
        $scope.tooltip = "This time has been inserted manually";
        setAction("Remove", "remove", "artificial");
      } else {
        setAction("Remove", "trash", "default");
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
