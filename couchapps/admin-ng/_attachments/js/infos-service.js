function InfosService() {

  this.raceName = function(infos, raceId) {
    console.log("In raceName with " + infos + " and " + raceId);
    return infos.races_names[raceId];
  };

  this.siteName = function(infos, siteId) {
    return infos.sites[siteId];
  };

}

angular.module("admin-ng").service("infosService", InfosService);
