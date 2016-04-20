function SentController($scope, changesService) {
  var ctrl = this;
  this.messages = [];

  changesService.initThenOnChange($scope, "replicate", "sms-distance",
      function(row) {
        ctrl.messages.push(row.doc);
      });
}

angular.module("admin-ng").component("sent", {
  templateUrl: "partials/sent-sms.html",
  controller: SentController,
  bindings: {
    bib: '<?'
  }
});
