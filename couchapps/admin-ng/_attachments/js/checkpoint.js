//
// Checkpoint
//

function CheckpointController($scope, stateService, dbService, changesService) {
  this.points = [];
  this.offset = 0;

  this.next = () => { this.offset -= this.itemsPerPage; this.loadCheckpoint(); };
  this.previous = () => { this.offset += this.itemsPerPage; this.loadCheckpoint(); };

  this.loadCheckpoint = changesService.serializedFunFactory(() =>
      dbService.getCheckpointsFrom(this.siteId, this.offset, this.itemsPerPage)
        .then(response => {
          this.points = response.data.rows.map(row => {
            return { timestamp: -row.key[1], bib: row.value };
          });
          this.nextEnabled = this.offset !== 0;
          this.previousEnabled = this.points.length === this.itemsPerPage;
        })
      );

  this.installChanges = () =>  {
    changesService.filterChanges($scope,
        change => change.doc.type == "checkpoint" && change.doc.site_id == this.siteId,
        this.loadCheckpoint);
    this.loadCheckpoint();
  };

  this.$routerOnActivate = (next, previous) => {
    stateService.installInfos($scope);
    this.siteId = Number(next.params.siteId.substring(1));
    this.itemsPerPage = 20;
    this.installChanges();
  };

  this.$onInit = () => {
    if (this.siteId !== undefined) {
      this.itemsPerPage = 2;
      this.installChanges();
    }
  };
}

angular.module("admin-ng").component("checkpoint", {
  templateUrl: "partials/checkpoint.html",
  controller: CheckpointController
});

angular.module("admin-ng").component("checkpointOverview", {
  templateUrl: "partials/checkpoint-overview.html",
  bindings: { siteId: "<", infos: "<" },
  controller: CheckpointController
});

//
// Checkpoints list
//

function CheckpointListController($scope, stateService) {
  stateService.installInfos($scope);
}

angular.module("admin-ng").component("checkpointList", {
  templateUrl: "partials/checkpoint-list.html",
  controller: CheckpointListController
});

//
// Routing component
//

angular.module("admin-ng").component("checkpointTop", {
  template: "<ng-outlet></ng-outlet>",
  $routeConfig: [
  {path: "/", component: "checkpointList", useAsDefault: true},
  {path: "/:siteId", name: "Checkpoint", component: "checkpoint"}
  ]
});
