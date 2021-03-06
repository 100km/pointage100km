angular.module("steenwerck.analysis", ["steenwerck.database", "steenwerck.state", "changes"]);

//
// Analysis list
//

function AnalysisListController($scope, stateService, dbService, changesService) {

  this.analyses = [];
  this.totalItems = 0;
  this.currentPage = 1;
  this.itemsPerPage = 20;

  stateService.installInfos($scope);

  this.loadAnalyses = changesService.serializedFunFactory(() =>
        dbService.getAnalysesFrom((this.currentPage - 1) * this.itemsPerPage, this.itemsPerPage)
          .then(response => {
            this.totalItems = response.data.total_rows;
            this.analyses = response.data.rows.map(row => row.value);
          }));

  changesService.filterChanges($scope, change => change.doc.type === "analysis", this.loadAnalyses);

  this.loadAnalyses();
}

angular.module("steenwerck.analysis").component("analysisList", {
  templateUrl: "partials/analysis-list.html",
  controller: AnalysisListController
});

//
// Analysis for one contestant
//

function AnalysisController($scope, stateService, changesService) {
  stateService.installInfos($scope);

  this.currentKms = 0;
  this.maxKms = 100;

  this.$routerOnActivate = (next, previous) => {
    this.bib = Number(next.params.bib);

    changesService.installAndCheck($scope, "analysis", "analysis-" + this.bib);

    $scope.$watch("analysis",
        analysis => {
          if (analysis) {
            this.analysis = analysis;
            this.needsFixing = analysis.anomalies > 0;
            var after = analysis.after;
            if (after.length > 0)
              this.currentKms = after[after.length - 1].distance;
            else
              this.currentKms = 0;
            if ($scope.infos !== undefined) {
              var raceId = analysis.race_id;
              this.maxKms = $scope.infos.kms_lap*($scope.infos.races_laps[raceId]-1) +
                $scope.infos.kms_offset[$scope.infos.kms_offset.length - 1];
            }
          }
        });

  };
}

angular.module("steenwerck.analysis").component("analysis", {
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

angular.module("steenwerck.analysis").component("analysisSummary", {
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

angular.module("steenwerck.analysis").directive("analysisPoint", () => {
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

angular.module("steenwerck.analysis").component("analysisTop", {
  template: "<ng-outlet></ng-outlet>",
  $routeConfig: [
  {path: "/", component: "analysisList", useAsDefault: true},
  {path: "/:bib", name: "Contestant", component: "analysis"}
  ]
});
