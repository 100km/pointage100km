angular.module("admin-ng").factory("stateService",
    ["changesService", "$rootScope",
    function(changesService, $rootScope) {

      var data = {
        contestants: {},
        analyses: {},
        installInfos: scope =>
          scope.$watch(() => $rootScope.infos, infos => scope.infos = $rootScope.infos)
      };

      // Infos and filters are continuously watched as we need this information all
      // the time.
      changesService.installAndCheck($rootScope, "infos", "infos");

      changesService.initThenFilterEach($rootScope, "common", "all_contestants",
          change => change.doc.type === "contestant",
          row => {
            var contestant = row.doc;
            contestant.fullName = contestant.first_name + " " + contestant.name;
            contestant.displayName = contestant.fullName + " (" + contestant.bib + ")";
            data.contestants[contestant.bib] = contestant;
          });

      return data;

}]);

angular.module("admin-ng")
        .component("contestant", {
          template: "<span ng-link=\"['/Analysis', 'Contestant', {bib: $ctrl.bib}]\">{{$ctrl.displayName}}<img ng-if=\"$ctrl.championship\" src=\"images/belgian_flag.svg\" class=\"flag\"></img></span> <small><race race-id=\"$ctrl.raceId\" infos=\"$ctrl.infos\"></race><span ng-if=\"$ctrl.teamName\"> – Team \"{{$ctrl.teamName}}\"</span></small>",
            bindings: { bib: "<", infos: "<" },
            controller: function($scope, stateService) {
              this.$onInit = () =>  {
                $scope.$watchGroup([() => stateService.contestants[this.bib], "bib"],
                    values => {
                      var contestant = values[0];
                      if (contestant) {
                        this.displayName = contestant.displayName;
                        this.raceId = contestant.race;
                        this.teamName = contestant.team_name;
                        this.championship = contestant.championship;
                      } else
                        this.displayName = "Bib " + this.bib;
                    });
              };
            }
        })
        .component("race", {
          template: "{{$ctrl.raceName}}",
          bindings: { raceId: "<", infos: "<" },
          controller: function() {
            this.$onChanges =
             () => this.raceName = this.infos ? this.infos.races_names[this.raceId] : "Race " + this.raceId;
          }
        })
        .component("site", {
          template: "<span ng-link=\"['/Checkpoints', 'Checkpoint', {siteId: 's' + $ctrl.siteId}]\">{{$ctrl.site}}</span>",
          bindings: { siteId: '<', infos: '<', format: '<?' },
          controller: function() {
            this.$onChanges = changes => {
              if (this.siteId !== undefined) {
                var siteN = "Site " + this.siteId;
                switch(this.format || "") {
                  case "short":
                    this.site = this.infos ? this.infos.sites_short[this.siteId] : siteN;
                    break;
                  case "name":
                    this.site = this.infos ? this.infos.sites[this.siteId] : siteN;
                    break;
                  default:
                    this.site = this.infos ? this.infos.sites[this.siteId] + " (" + this.siteId + ")" : siteN;
                }
              }
            };
          }
        });
