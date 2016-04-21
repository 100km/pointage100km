function AlertsController($scope, changesService, stateService) {
  var ctrl = this;
  this.alertsSet = {};
  this.alerts = [];

  var mapSeverity = function(severity) {
    switch (severity) {
      case "critical": return "danger";
      case "error": return "danger";
      case "warning": return "warning";
      case "info": return "info";
      default: return "default";
    }
  };

  this.$routerOnActivate = function(next, previous) {
    return stateService.installInfo($scope);
  };

  changesService.initThenOnChange($scope, "admin", "alerts", 
      function(change) {
        var alert = change.doc;
        alert.level = mapSeverity(alert.severity);
        ctrl.alertsSet[alert._id] = alert;
        if (alert.cancelledTS) {
          ctrl.alerts = [];
          angular.forEach(ctrl.alertsSet, function(a) { ctrl.alerts.push(a); });
          ctrl.alerts.sort(function(a, b) { return b.addedTS - a.addedTS; });
        } else {
          ctrl.alerts.unshift(alert);
        }
      });
}

angular.module("admin-ng").component("alerts", {
  templateUrl: "partials/alerts.html",
  controller: AlertsController
});
