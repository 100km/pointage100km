var app = angular.module("admin-ng", ["ngComponentRouter", "ui.bootstrap", "pubsub",
    "txx.diacritics"]);

app.filter("fullName", function() {
  return doc => {
    if (doc.name)
      return doc.first_name + " " + doc.name + " (dossard " + doc.bib + ")";
    else
      return "Dossard " + doc.bib;
  };
});

app.filter("gravatarUrl", function() {
  return doc => {
    return "http://www.gravatar.com/avatar/" + CryptoJS.MD5(angular.lowercase(doc.email));
  };
});

app.filter("digitsUnit", ["$filter", function($filter) {
  return (x, digits, unit) =>
    x === undefined ? "" : ($filter('number')(x, digits) + " " + unit);
}]);

function RootController($scope) {

  // What a ugly hack
  this.onSelection = bib =>
    $scope.$$ngOutlet.$$outlet.router.navigate(["/Analysis", "Contestant", {bib: bib}]);
}

app.constant("database", "../../");
app.value("$routerRootComponent", "app");
app.component("app", {
  templateUrl: "partials/admin-ng.html",
  $routeConfig: [
    {path: "/analysis/...", name: "Analysis", component: "analysisTop", useAsDefault: true},
    {path: "/checkpoint/...", name: "Checkpoints", component: "checkpointTop"},
    {path: "/messages/", name: "Messages", component: "messages"},
    {path: "/sms/", name: "SMS", component: "sent"},
    {path: "/alerts/", name: "Alerts", component: "alerts"},
  ],
  controller: RootController
});
app.run(["changesService",
    function(changesService) {
      changesService.globalChangesStart();
    }]);
