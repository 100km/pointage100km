function SentController($scope, changesService, dbService) {
  this.messages = [];

  // We cannot use this function as this will not build the complete
  // route (/analysis/:bib), but one with a query parameter instead
  // (/analysis?bib=bib). Unfortunately, this one will not get matched
  // by the router for Analysis.
  this.onSelectBib = bib => this.$router.navigate(["/Analysis", "Contestant", {bib: bib}]);

  this.totalItems = 0;
  this.currentPage = 1;

  this.$onInit = () => {

    if (this.bib === undefined) {

      this.itemsPerPage = 20;
      this.loadSMS = changesService.serializedFunFactory(() =>
          dbService.getSMSFrom((this.currentPage - 1) * this.itemsPerPage, this.itemsPerPage)
          .then(response => {
            this.totalItems = response.data.total_rows;
            this.messages = response.data.rows.map(row => row.doc);
          }));
      changesService.filterChanges($scope,
          change => change.doc.type === "sms",
          this.loadSMS);

    } else {

      this.itemsPerPage = 5;
      this.loadSMS = changesService.serializedFunFactory(() =>
          dbService.getSMSFor(this.bib)
          .then(response => {
            this.totalItems = response.data.rows.length;
            this.messages = response.data.rows.map(row => row.doc);
            this.messages.splice(0, (this.currentPage - 1) * this.itemsPerPage);
            this.messages.splice(this.itemsPerPage);
          }));
      changesService.filterChanges($scope,
          change => change.doc.type === "sms" && this.bib == change.doc.bib,
          this.loadSMS);

      }

    this.loadSMS();

  };

}

angular.module("steenwerck.sms", ["steenwerck.database", "changes"])
  .component("sent", {
    templateUrl: "partials/sent-sms.html",
    controller: SentController,
    bindings: {
      bib: '<?',
      $router: '<'
    }
  });
