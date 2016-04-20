function SentController($scope, changesService) {
  var ctrl = this;
  this.messages = [];

  // We cannot use this function as this will not build the complete
  // route (/analysis/:bib), but one with a query parameter instead
  // (/analysis?bib=bib). Unfortunately, this one will not get matched
  // by the router for Analysis.
  this.onSelectBib = function(bib) {
    console.log("bib: ", bib);
    console.log("$router: ", ctrl.$router);
    ctrl.$router.navigate(["Analysis", {bib: bib}]);
  }

  changesService.initThenOnChange($scope, "replicate", "sms-distance",
      function(row) {
        ctrl.messages.push(row.doc);
      });
}

angular.module("admin-ng").component("sent", {
  templateUrl: "partials/sent-sms.html",
  controller: SentController,
  bindings: {
    bib: '<?',
    $router: '<'
  }
});
