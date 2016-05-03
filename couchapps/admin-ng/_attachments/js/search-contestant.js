function SearchContestantController($scope, stateService, removeDiacritics) {

  this.replace = removeDiacritics.replace;

  this.placeholder = this.placeholder || "Name/bib";

  $scope.$watchCollection(() => stateService.contestants, 
      contestants => {
        $scope.contestants = [];
        angular.forEach(contestants,
            c => $scope.contestants.push({bib: c.bib, searchName: c.searchName,
              searchMatch: c.searchMatch}));
      });

  this.select = contestant => {
    if (contestant) {
      $scope.search = "";
      this.onSelection({bib: contestant.bib});
    }
  };
}

angular.module("steenwerck.search", ["steenwerck.state", "txx.diacritics"])
  .component("searchContestant", {
    templateUrl: "partials/search-contestant.html",
    controller: SearchContestantController,
    bindings: { "onSelection": "&", "placeholder": "@?" }
  });
