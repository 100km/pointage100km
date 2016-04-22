function SentController($scope, changesService) {
  var ctrl = this;
  this.messages = [];

  // We cannot use this function as this will not build the complete
  // route (/analysis/:bib), but one with a query parameter instead
  // (/analysis?bib=bib). Unfortunately, this one will not get matched
  // by the router for Analysis.
  this.onSelectBib = function(bib) {
    ctrl.$router.navigate(["Analysis", {bib: bib}]);
  };

  changesService.initThenFilterEach($scope, "replicate", "sms-distance",
      function(change) { return change.doc.type === "sms"; },
      function(change) { ctrl.messages.push(change.doc); },
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
