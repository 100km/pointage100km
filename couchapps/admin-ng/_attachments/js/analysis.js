//
// Analysis list
//

function AnalysisListController($http, database) {
  var ctrl = this;

  this.$onInit = function() {
    return $http.get(database + "/_design/admin-ng/_view/by-anomalies")
      .then(function(response) {
        ctrl.anomalies = response.data.rows.map(function(o) { return o.value; });
      });
  };
}

angular.module("admin-ng").component("analysisList", {
  templateUrl: "partials/analysis-list.html",
  controller: AnalysisListController
});

//
// Analysis for one contestant
//

function AnalysisController($scope, dbService, changesService) {
  var ctrl = this;
  ctrl.needsFixing = false;

  ctrl.loadAnalysis = function() {
    return dbService.enrichedAnalysis(ctrl.bib).then(function(analysis) {
      ctrl.analysis = analysis;
      ctrl.needsFixing = analysis.anomalies > 0;
    });
  };

  this.$routerOnActivate = function(next, previous) {
    ctrl.bib = Number(next.params.bib);

    dbService.infos.then(function(infos) { ctrl.infos = infos; });

    // If either the document or the contestant information changes,
    // we want to reload a fresh analysis.
    changesService.onChange($scope, {
      filter: "_doc_ids",
      doc_ids: '["contestant-' + ctrl.bib + '","analysis-' + ctrl.bib + '"]',
      heartbeat: 30000, since: "now"
    },
    ctrl.loadAnalysis);

    // Wait until we have loaded the initial analysis before continuing.
    return ctrl.loadAnalysis();
  };
}

angular.module("admin-ng").component("analysis", {
  templateUrl: "partials/analysis.html",
  controller: AnalysisController,
});

//
// Analysis summary (or before/after)
//

function AnalysisSummaryController() {
}

angular.module("admin-ng").component("analysisSummary", {
  templateUrl: "partials/analysis-summary.html",
  controller: AnalysisSummaryController,
  bindings: {
    bib: '<',
    points: '<',
    analysis: '<',
    active: '<',
    infos: '<',
    addAction: '&',
  }
});

//
// Represent a given point (table row) in the analysis
//

function AnalysisPointController($scope) {
  var ctrl = $scope;
  this.$onInit = function() {
    ctrl.site = ctrl.infos.sites[ctrl.point.site_id] + " (" + ctrl.point.site_id + ")";

    if (ctrl.active) {
      var setDisplay = function(label, icon, clazz, tooltip) {
        ctrl.action_label = label;
        ctrl.action_icon = icon;
        ctrl.action_class = clazz || ctrl.point.type;
        ctrl.tooltip = tooltip;
      };
      var setAction = function(action, isSuggested) {
        ctrl.action = {docid: "checkpoints-" + ctrl.point.site_id + "-" + ctrl.bib,
          action: action, time: ctrl.point.time};
        if (isSuggested) {
          // FIXME: Add to global actions here
        }
      };
      ctrl.act = function() {
        console.log("Performing action " + JSON.stringify(ctrl.action));
      };
      if (ctrl.point.action === "add") {
        setDisplay("Add", "plus-sign", "success", "The algorithm suggests to add this point");
        setAction("add", true);
      } else if (ctrl.point.action === "remove") {
        setDisplay("Remove", "trash", "danger", "The algorithm suggests to delete this point");
        setAction("remove", true);
      } else if (ctrl.point.type === "deleted") {
        setDisplay("Restore", "plus-sign", "deleted", "This time has been previously deleted");
        setAction("add");
      } else if (ctrl.point.type === "down") {
        setDisplay();
      } else if (ctrl.point.type === "artificial") {
        setDisplay("Remove", "remove", "artificial", "This time has been inserted manually");
        setAction("remove");
      } else {
        setDisplay("Remove", "trash");
        setAction("remove");
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
      addAction: "&",
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
  {path: "/", component: "analysisList", useAsDefault: true},
  {path: "/:bib", name: "Analysis", component: "analysis"}
  ]
});
