function DbService($http, database) {

  var $service = this;

  this.infos = $http.get(database + "/infos").then(function (r) { return r.data; });

  this.contestant = function(contestantId) {
    return $http.get(database + "/contestant-" + contestantId)
      .then(function (r) {
        contestant = r.data;
        contestant.full_name = contestant.first_name + " " + contestant.name +
          " (bib " + contestantId + ")";
        return contestant;
      });
  };

  this.analysis = function(contestantId) {
    return $http.get(database + "/analysis-" + contestantId)
      .then(function (r) { return r.data; });
  };

  this.enrichedAnalysis = function(contestantId) {
    return $service.analysis(contestantId).then(function (analysis) {
      analysis.full_name = "Bib " + contestantId;
      $service.contestant(contestantId).then(function (contestant) {
        analysis.full_name = contestant.full_name;
      }, function (reason) {
        analysis.full_name = analysis.full_name + " (no detailed info)";
      });
      return analysis;
    });
  };
}

angular.module("admin-ng").service("dbService", DbService);
