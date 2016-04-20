angular.module("admin-ng").factory("changesService", ["database", "$http", "$httpParamSerializer", "$timeout",
    function(database, $http, $httpParamSerializer, $timeout) {
      var onChange = function(scope, params, callback) {
        var ev;
        var allParams = angular.merge({feed: "eventsource"}, params);

        // The reconnection delay after an error will start at 10ms
        // and will be doubled until it reaches 20.48s.
        var reconnectionDelay;

        var reconnect = function() {
          ev = new EventSource(database + "/_changes?" + $httpParamSerializer(allParams));
          ev.onopen = function() {
            reconnectionDelay = undefined;
          };
          ev.onmessage = function(event) {
            allParams.since = event.lastEventId;
            scope.$applyAsync(function() { callback(JSON.parse(event.data)); });
          };
          ev.onerror = function(event) {
            if (ev.readyState === EventSource.CLOSED) {
              if (!reconnectionDelay)
                reconnectionDelay = 10;
              else if (reconnectionDelay < 20480)
                reconnectionDelay *= 2;
              ev.close();
              $timeout(reconnect, reconnectionDelay, false);
            }
          };
        };

        reconnect();
        scope.$on("$destroy", function() { ev.close(); });
      };

      var initThenOnChange = function(scope, design, view, callback, viewParams) {
        $http.get(database + "/_design/" + design + "/_view/" + view + "?include_docs=true&update_seq=true&reduce=false").then(function(response) {
          angular.forEach(response.data.rows, function(row) { callback(row); });
          onChange(scope, {filter: "_view", view: design + "/" + view,
            since: response.data.update_seq,
            include_docs: true, heartbeat: 30000}, callback);
        });
      };

      return {
        onChange: onChange,
        initThenOnChange: initThenOnChange
      };
    }]);

angular.module("admin-ng").factory("globalChangesService", ["changesService", "$rootScope", "database", "$http",
    function(changesService, $rootScope, database, $http) {
      return {
        start: function() {
          changesService.onChange($rootScope, {since: "now", heartbeat: 3000, include_docs: true},
              function(change) {
                $rootScope.$broadcast("change", change);
              });
        }
      };
    }]);
