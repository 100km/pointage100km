angular.module("admin-ng").factory("changesService", ["database", "$httpParamSerializer", function(database, $httpParamSerializer) {
  return {
    onChange: function(scope, params, callback) {
      var ev;
      var allParams = angular.merge({feed: "eventsource"}, params);

      var reconnect = function() {
        ev = new EventSource(database + "/_changes?" + $httpParamSerializer(allParams));
        ev.onmessage = function(event) {
          allParams.since = event.lastEventId;
          scope.$applyAsync(function() { callback(JSON.parse(event.data)); });
        };
        ev.onerror = function(event) {
          if (ev.readyState === EventSource.CLOSED) {
            ev.close();
            reconnect();
          }
        };
      };

      reconnect();
      scope.$on("$destroy", function() { ev.close(); });
    }
  };
}]);

angular.module("admin-ng").factory("globalChangesService", ["changesService", "$rootScope", "database", "$http",
    function(changesService, $rootScope, database, $http) {
      return {
        start: function() {
          $http.get(database).then(function(response) {
            var status = response.data;
            var sequence = status.update_seq;
            changesService.onChange($rootScope, {since: sequence, heartbeat: 3000, include_docs: true},
                function(change) {
                  $rootScope.$broadcast("change", change);
                });
          });
        }
      };
    }]);
