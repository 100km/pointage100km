var app = angular.module("inscriptionsSms", ["ui.bootstrap", "pubsub", "changes",
    "steenwerck.database", "steenwerck.search", "steenwerck.state",
    "steenwerck.stalking"]);

app.constant("database", "../../");

app.component("app", {
  templateUrl: "partials/stalking-fr.html",
  controller: StalkingController
});
