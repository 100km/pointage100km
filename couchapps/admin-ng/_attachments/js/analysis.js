//
// Analysis list
//

function AnalysisListController($scope, stateService) {
  this.analyses = [];

  stateService.installInfos($scope);
  $scope.$watchCollection(() => stateService.analyses, analyses => {
    this.analyses = [];
    angular.forEach(analyses, a => this.analyses.push(a));
  });
}

angular.module("admin-ng").component("analysisList", {
  templateUrl: "partials/analysis-list.html",
  controller: AnalysisListController
});

//
// Analysis for one contestant
//

function AnalysisController($scope, stateService) {
  stateService.installInfos($scope);

  this.$routerOnActivate = (next, previous) => {
    this.bib = Number(next.params.bib);

    $scope.$watch(() => stateService.analyses[this.bib],
        analysis => {
          if (analysis) {
            this.analysis = analysis;
            this.needsFixing = analysis.anomalies > 0;
          }
        });

  };
}

angular.module("admin-ng").component("analysis", {
  templateUrl: "partials/analysis.html",
  controller: AnalysisController,
});

//
// Analysis summary (or before/after)
//

function AnalysisSummaryController(dbService) {
  this.act = (siteId, timestamp, action) =>
    dbService.fixCheckpoint(this.analysis.bib, this.analysis.race_id, siteId, timestamp, action);
}

angular.module("admin-ng").component("analysisSummary", {
  templateUrl: "partials/analysis-summary.html",
  controller: AnalysisSummaryController,
  bindings: {
    points: '<',
    analysis: '<',
    active: '<',
    infos: '<'
  }
});

//
// Represent a given point (table row) in the analysis
//

function AnalysisPointController($scope, stateService) {
  this.$onInit = () => {
    if ($scope.active) {
      var setDisplay = (action, label, icon, clazz, tooltip) => {
        $scope.action = action;
        // FIXME: Add to global actions here if needed
        $scope.action_label = label;
        $scope.action_icon = icon;
        $scope.action_class = clazz || $scope.point.type;
        $scope.tooltip = tooltip;
      };
      $scope.act = () => $scope.upperAct({action: $scope.action});
      if ($scope.point.action === "add") {
        setDisplay("add", "Add", "plus-sign", "success", "The algorithm suggests to add this point");
      } else if ($scope.point.action === "remove") {
        setDisplay("remove", "Remove", "trash", "danger", "The algorithm suggests to delete this point");
      } else if ($scope.point.type === "deleted") {
        setDisplay("add", "Restore", "plus-sign", "deleted", "This time has been previously deleted");
      } else if ($scope.point.type === "down") {
        setDisplay();
      } else if ($scope.point.type === "artificial") {
        setDisplay("remove", "Remove", "remove", "artificial", "This time has been inserted manually");
      } else {
        setDisplay("remove", "Remove", "trash");
      }
    }
  };

}

angular.module("admin-ng").directive("analysisPoint", () => {
  return {
    templateUrl: "partials/analysis-point.html",
    controller: AnalysisPointController,
    restrict: "A",
    replace: true,
    scope: {
      infos: "<",
      point: "<",
      upperAct: "&act",
      active: "<"
    }
  };
});

//
// Routing component
//

angular.module("admin-ng").component("analysisTop", {
  template: "<ng-outlet></ng-outlet>",
  $routeConfig: [
  {path: "/", name: "AnalysisList", component: "analysisList", useAsDefault: true},
  {path: "/:bib", name: "Analysis", component: "analysis"}
  ]
});
