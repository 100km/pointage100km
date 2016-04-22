function SentController($scope, changesService) {
  this.messages = [];

  // We cannot use this function as this will not build the complete
  // route (/analysis/:bib), but one with a query parameter instead
  // (/analysis?bib=bib). Unfortunately, this one will not get matched
  // by the router for Analysis.
  this.onSelectBib = bib => this.$router.navigate(["Analysis", {bib: bib}]);

  changesService.initThenFilterEach($scope, "replicate", "sms-distance",
      change => change.doc.type === "sms",
      change => this.messages.push(change.doc),
      true);
}

angular.module("admin-ng").component("sent", {
  templateUrl: "partials/sent-sms.html",
  controller: SentController,
  bindings: {
    bib: '<?',
    $router: '<'
  }
});
