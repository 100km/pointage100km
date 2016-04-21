var app = angular.module("admin-ng", ["ngComponentRouter", "ui.bootstrap", "pubsub"]);

app.filter("fullName", function() {
  return function(doc) {
    if (doc.name)
      return doc.first_name + " " + doc.name + " (dossard " + doc.bib + ")";
    else
      return "Dossard " + doc.bib;
  };
});

app.filter("gravatarUrl", function() {
  return function(doc) {
    return "http://www.gravatar.com/avatar/" + CryptoJS.MD5(angular.lowercase(doc.email));
  };
});

app.filter("digitsUnit", ["$filter", function($filter) {
  return function(x, digits, unit) {
    return x === undefined ? "" : ($filter('number')(x, digits) + " " + unit);
  };
}]);

app.constant("database", "../../");
app.value("$routerRootComponent", "app");
app.component("app", {
  templateUrl: "partials/admin-ng.html",
  $routeConfig: [
    {path: "/analysis/...", name: "Analysis", component: "analysisTop", useAsDefault: true},
    {path: "/sms/", name: "SMS", component: "sent"},
    {path: "/alerts/", name: "Alerts", component: "alerts"},
  ]
});
app.run(["changesService",
    function(changesService) {
      changesService.globalChangesStart();
    }]);
