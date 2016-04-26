function AlertsController($scope, changesService, stateService, dbService) {
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

  this.totalItems = 0;
  this.currentPage = 1;
  this.itemsPerPage = 20;

  this.loadAlerts = () =>
        dbService.getAlertsFrom((this.currentPage - 1) * this.itemsPerPage, this.itemsPerPage)
          .then(response => {
            this.totalItems = response.data.total_rows;
            this.alerts = response.data.rows.map(row => row.value);
          });

  // Ensure that not more than one reload is active at the same time. Newer reload triggers
  // will be executed when this one is finished.
  this.loading = false;
  this.mustReload = false;
  this.reloadAlerts = () => {
    if (this.loading)
      this.mustReload = true;
    else {
      this.loading = true;
      this.mustReload = false;
      this.loadAlerts().then(() => {
        this.loading = false;
        if (this.mustReload)
          this.reloadAlerts();
      });
    }
  };

  changesService.filterChanges($scope, change => change.doc.type === "alert",
      () => this.reloadAlerts());

  this.reloadAlerts();

}

angular.module("admin-ng").component("alerts", {
  templateUrl: "partials/alerts.html",
  controller: AlertsController
});
