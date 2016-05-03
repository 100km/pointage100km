function StalkingController($scope, dbService, stateService, $timeout) {

  stateService.installInfos($scope);

  $scope.$watch(() => this.bib, () => document.getElementById("phone").focus());

  this.onSelection = bib => {
    this.bib = bib;
    dbService.getStalkers(bib).then(stalkers => this.stalkers = stalkers);
    $timeout(() => document.getElementById("phone").focus(), 10);
  };

  this.normalize = number => {
    number = number || "";
    number = number.replace(/ /g, "");
    if (number.substring(0, 2) === "00")
      number = "+" + number.substring(2);
    else if (number.substring(0, 2) === "04")
      number = "+32" + number.substring(1);
    else if (number[0] === "0")
      number = "+33" + number.substring(1);
    if (number.substring(0, 2) == "+3" && number[3] === "0")
      number = number.substring(0, 3) + number.substring(4);
    return number;
  };

  this.formatNumber = number => {
    if (number.substring(0, 3) === "+33")
      number = "+33 " + number.substring(3, 4) + " " + number.substring(4, 6) + " " +
        number.substring(6, 8) + " " + number.substring(8, 10) + " " +
        number.substring(10, 12);
    else if (number.substring(0, 3) === "+32")
      number = "+32 " + number.substring(3, 6) + " " + number.substring(6, 8) + " " +
        number.substring(8, 10) + " " + number.substring(10, 12);
    return number;
  };

  this.invalidate = normalized => {
    if (this.stalkers && this.stalkers.indexOf(normalized) > -1)
      return "Number already present";
    if (normalized.length === 0)
      return false;
    if (normalized.length < 3)
      return "Number too short";
    var prefix = normalized.substring(0, 3);
    if (prefix !== "+32" && prefix != "+33")
      return "Only French and Belgian phone numbers are accepted";
    if (normalized.length < 4)
      return false;
    var code = normalized[3];
    if (prefix === "+33" && code !== "6" && code != "7")
      return "This French number is not a mobile line";
    if (prefix === "+32" && code !== "4")
      return "This Belgian number is not a mobile line";
  };

  this.addStalker = () => {
    var normalized = this.normalize(this.phone);
    if (!this.invalidate(normalized)) {
      dbService.addStalker(this.bib, normalized).then(response => this.stalkers = response.data);
      this.phone = "";
    }
  };

  this.removeStalker = stalker =>
    dbService.removeStalker(this.bib, stalker).then(response => this.stalkers = response.data);
}

angular.module("steenwerck.stalking",
    ["steenwerck.database", "steenwerck.state", "steenwerck.search", "changes"])
  .component("stalking", {
    templateUrl: "partials/stalking.html",
    controller: StalkingController
  });
