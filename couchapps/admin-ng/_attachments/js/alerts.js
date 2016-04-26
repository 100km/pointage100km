function AlertsController($scope, changesService, stateService, dbService) {
  this.alertsSet = {};
  this.alerts = [];

  this.mapSeverity = severity => {
    switch (severity) {
      case "critical": return "danger";
      case "error": return "danger";
      case "warning": return "warning";
      case "info": return "info";
      default: return "default";
    }
  };

  this.$routerOnActivate = (next, previous) => stateService.installInfos($scope);

  this.totalItems = 0;
  this.currentPage = 1;
  this.itemsPerPage = 20;

  this.loadAlerts = changesService.serializedFunFactory(() =>
        dbService.getAlertsFrom((this.currentPage - 1) * this.itemsPerPage, this.itemsPerPage)
          .then(response => {
            this.totalItems = response.data.total_rows;
            this.alerts = response.data.rows.map(row => row.value);
          }));

  changesService.filterChanges($scope, change => change.doc.type === "alert", this.loadAlerts);

  this.loadAlerts();

}

angular.module("admin-ng").component("alerts", {
  templateUrl: "partials/alerts.html",
  controller: AlertsController
});
