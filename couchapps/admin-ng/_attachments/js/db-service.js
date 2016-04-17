function DbService($http, database) {

  var $service = this;

  this.infos = $http.get(database + "/infos").then(function (r) { return r.data; });

  this.contestant = function(contestantId) {
    return $http.get(database + "/contestant-" + contestantId)
      .then(function (r) { return r.data; });
  };

  this.analysis = function(contestantId) {
    return $http.get(database + "/analysis-" + contestantId)
      .then(function (r) { return r.data; });
  };

  this.enrichedAnalysis = function(contestantId) {
    return $service.analysis(contestantId).then(function (analysis) {
      return $service.contestant(contestantId).then(function (contestant) {
        analysis.first_name = contestant.first_name;
        analysis.name = contestant.name;
        analysis.full_name = analysis.first_name + " " + analysis.name +
          " (bib " + contestantId + ")";
        return analysis;
      });
    });
  };
}

angular.module("admin-ng").service("dbService", DbService);
