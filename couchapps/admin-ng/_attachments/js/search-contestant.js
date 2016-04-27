function SearchContestantController($scope, stateService, removeDiacritics) {

  // this.replace = removeDiacritics.replace;
  this.replace = input => {
    var result = removeDiacritics.replace(input);
    console.log(input, result);
    return result;
  };

  $scope.$watchCollection(() => stateService.contestants, 
      contestants => {
        $scope.contestants = [];
        angular.forEach(contestants,
            c => $scope.contestants.push({bib: c.bib, searchName: c.searchName,
              searchMatch: c.searchMatch}));
      });

  this.select = contestant => {
    if (contestant) {
      this.onSelection({bib: contestant.bib});
      $scope.search = "";
    }
  };
}

angular.module("admin-ng").component("searchContestant", {
  templateUrl: "partials/search-contestant.html",
  controller: SearchContestantController,
  bindings: { "onSelection": "&" }
});
