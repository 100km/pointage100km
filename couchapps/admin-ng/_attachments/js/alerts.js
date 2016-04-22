function AlertsController($scope, changesService, stateService) {
  this.alertsSet = {};
  this.alerts = [];

  var mapSeverity = severity => {
    switch (severity) {
      case "critical": return "danger";
      case "error": return "danger";
      case "warning": return "warning";
      case "info": return "info";
      default: return "default";
    }
  };

  this.$routerOnActivate = (next, previous) => stateService.installInfos($scope);

  changesService.initThenFilterEach($scope, "admin", "alerts",
      change => change.doc.type === "alert",
      change => {
        var alert = change.doc;
        alert.level = mapSeverity(alert.severity);
        this.alertsSet[alert._id] = alert;
        if (alert.cancelledTS) {
          this.alerts = [];
          angular.forEach(this.alertsSet, a => this.alerts.push(a));
          this.alerts.sort((a, b) => b.addedTS - a.addedTS);
        } else {
          this.alerts.unshift(alert);
        }
      }, true);
}

angular.module("admin-ng").component("alerts", {
  templateUrl: "partials/alerts.html",
  controller: AlertsController
});
