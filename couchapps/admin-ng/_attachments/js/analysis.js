//
// Analysis list
//

function AnalysisListController($scope, stateService) {
  var ctrl = this;
  this.analyses = [];

  $scope.$watchCollection(function() { return stateService.analyses; }, function(analyses) {
    ctrl.analyses = [];
    angular.forEach(analyses, function(a) { ctrl.analyses.push(a); });
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
  var ctrl = this;

  this.$routerOnActivate = function(next, previous) {
    ctrl.bib = Number(next.params.bib);

    $scope.$watch(function() { return stateService.analyses[ctrl.bib]; },
        function(analysis) {
          if (analysis) {
            ctrl.analysis = analysis;
            ctrl.needsFixing = analysis.anomalies > 0;
          }
        });

    $scope.$watch(function() { return stateService.infos; },
        function(infos) {
          ctrl.infos = infos;
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

function AnalysisSummaryController($scope, $http, database) {
  var ctrl = this;

  this.act = function(siteId, timestamp, action) {
    var docid = "checkpoints-" + siteId + "-" + ctrl.analysis.bib;
    var payload = {
      bib: ctrl.analysis.bib, race_id: ctrl.analysis.race_id,
      site_id: siteId, timestamp: timestamp, action: action
    };
    $http.put(database + "/_design/admin-ng/_update/fix-checkpoint/" + docid, payload);
  };
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
  var ctrl = $scope;
  this.$onInit = function() {
    ctrl.site = "Site " + ctrl.point.site_id;
    $scope.$watch(function() { return stateService.infos; },
        function(infos) {
          if (infos)
            ctrl.site = ctrl.infos.sites[ctrl.point.site_id] + " (" + ctrl.point.site_id + ")";
        });

    if (ctrl.active) {
      var setDisplay = function(action, label, icon, clazz, tooltip) {
        ctrl.action = action;
        // FIXME: Add to global actions here if needed
        ctrl.action_label = label;
        ctrl.action_icon = icon;
        ctrl.action_class = clazz || ctrl.point.type;
        ctrl.tooltip = tooltip;
      };
      ctrl.act = function() {
        ctrl.upperAct({action: ctrl.action});
      };
      if (ctrl.point.action === "add") {
        setDisplay("add", "Add", "plus-sign", "success", "The algorithm suggests to add this point");
      } else if (ctrl.point.action === "remove") {
        setDisplay("remove", "Remove", "trash", "danger", "The algorithm suggests to delete this point");
      } else if (ctrl.point.type === "deleted") {
        setDisplay("add", "Restore", "plus-sign", "deleted", "This time has been previously deleted");
      } else if (ctrl.point.type === "down") {
        setDisplay();
      } else if (ctrl.point.type === "artificial") {
        setDisplay("remove", "Remove", "remove", "artificial", "This time has been inserted manually");
      } else {
        setDisplay("remove", "Remove", "trash");
      }
    }
  };

}

angular.module("admin-ng").directive("analysisPoint", function() {
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
